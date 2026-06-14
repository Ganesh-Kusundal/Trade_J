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

    private final DuckDbAnalyticsEngine engine;
    private final AtomicReference<Cached> cache = new AtomicReference<>(Cached.empty());

    public HistoricalDataCatalog(DuckDbAnalyticsEngine engine) {
        this.engine = engine;
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
        if (current.isFresh()) {
            return current;
        }
        try {
            AnalyticsCatalogSnapshot snap = engine.catalogSnapshot();
            Cached next = new Cached(snap, snap.equitySymbolCount() > 0, System.nanoTime() + CACHE_TTL.toNanos());
            cache.set(next);
            return next;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to build analytics catalog snapshot", ex);
        } catch (RuntimeException ex) {
            // Caching the failure prevents the next probe from re-running
            // the heavy path; the next refresh will retry.
            Cached failed = new Cached(null, false, System.nanoTime() + CACHE_TTL.toNanos());
            cache.set(failed);
            return failed;
        }
    }

    private record Cached(AnalyticsCatalogSnapshot snapshot, boolean healthy, long expiresAtNanos) {
        static Cached empty() {
            return new Cached(null, false, 0L);
        }
        boolean isFresh() {
            return expiresAtNanos > System.nanoTime();
        }
    }
}
