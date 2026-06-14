package com.tradej.analytics.catalog;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsCatalogSnapshot;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public final class HistoricalDataCatalog {

    /** How long a healthy()/snapshot() result is reused before recomputing.
     *  The underlying DuckDB queries and parquet file scan can take 40+
     *  seconds on a cold warehouse, which would block the composite
     *  /actuator/health endpoint. We cache for this duration so
     *  probes return instantly with a possibly-stale view. */
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    /**
     * Read-only view of the analytics engine's catalog. The
     * production binding is {@link DuckDbAnalyticsEngine}; tests
     * can supply a stub via the package-private constructor.
     */
    public interface SnapshotSource {
        AnalyticsCatalogSnapshot read() throws SQLException;
    }

    private final SnapshotSource source;
    private final Duration cacheTtl;
    private final AtomicReference<Cached> cache = new AtomicReference<>(Cached.empty());

    public HistoricalDataCatalog(DuckDbAnalyticsEngine engine) {
        this(engine, CACHE_TTL);
    }

    /** Test-only constructor that takes a stub source and a custom TTL. */
    HistoricalDataCatalog(SnapshotSource source, Duration ttl) {
        this.source = source;
        this.cacheTtl = ttl;
    }

    /** Production constructor — wraps the engine in a SnapshotSource. */
    HistoricalDataCatalog(DuckDbAnalyticsEngine engine, Duration ttl) {
        this.source = engine::catalogSnapshot;
        this.cacheTtl = ttl;
    }

    public AnalyticsCatalogSnapshot snapshot() {
        Cached current = cache.get();
        if (current.isFresh()) {
            return current.snapshot;
        }
        Cached fresh = computeAndCache();
        return fresh.snapshot;
    }

    public boolean healthy() {
        Cached current = cache.get();
        if (current.isFresh()) {
            return current.healthy;
        }
        Cached fresh = computeAndCache();
        return fresh.healthy;
    }

    private synchronized Cached computeAndCache() {
        Cached current = cache.get();
        if (current.isFresh(cacheTtl)) {
            return current;
        }
        Cached failed;
        try {
            AnalyticsCatalogSnapshot snap = source.read();
            Cached next = new Cached(snap, snap.equitySymbolCount() > 0, System.nanoTime() + cacheTtl.toNanos());
            cache.set(next);
            return next;
        } catch (Exception ex) {
            // Cache the failure so a probe storm during a warehouse
            // outage doesn't keep retrying the heavy path. The
            // caller still gets the exception (re-thrown below) so
            // the user sees a clear "analytics down" signal; the
            // next refresh after the TTL elapses will retry.
            failed = new Cached(null, false, System.nanoTime() + cacheTtl.toNanos());
            cache.set(failed);
            if (ex instanceof RuntimeException re) throw re;
            if (ex instanceof SQLException sqle) {
                throw new IllegalStateException("Failed to build analytics catalog snapshot", sqle);
            }
            throw new IllegalStateException("Failed to build analytics catalog snapshot", ex);
        }
    }

    private record Cached(AnalyticsCatalogSnapshot snapshot, boolean healthy, long expiresAtNanos) {
        static Cached empty() {
            return new Cached(null, false, 0L);
        }
        boolean isFresh(Duration ttl) {
            return expiresAtNanos > System.nanoTime() && ttl != null;
        }
        boolean isFresh() {
            return isFresh(Duration.ofSeconds(5));
        }
    }
}
