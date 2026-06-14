package com.tradej.broker.dhan.auth;

import com.tradej.broker.dhan.auth.DhanAuthenticationException;
import com.tradej.broker.dhan.auth.DhanAuthRejectedException;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.exceptions.DhanBrokerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Periodic, out-of-band revalidation of the cached Dhan access token against
 * the broker's authoritative {@code /v2/profile} endpoint.
 *
 * <p><b>Why:</b> The persisted {@code expiryEpochMs} in
 * {@code runtime/dhan-token-state.json} is a snapshot from the time the token
 * was minted. The broker can shrink the validity window at any time
 * (admin revocation, session timeout, TOTP re-mint from another client).
 * Without a background revalidation, {@code ensureValid()} trusts a stale
 * value until the next order triggers a profile call — by which point the
 * strategy has already paid 2-3 retries on rejected orders.
 *
 * <p><b>How:</b> Every {@link #intervalMs()}, fetches {@code /v2/profile} and
 * calls {@link DhanTokenManager#updateCachedExpiry(long)} to keep the
 * cached {@code expiryEpochMs} in sync. On a 401 the cached token is
 * invalidated so the next {@code ensureValid()} mints a fresh TOTP.
 *
 * <p>This class is intentionally opt-in: instantiate it once from
 * Spring composition and {@link #start()} it. {@link #stop()} is idempotent.
 */
public final class DhanTokenRevalidator implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DhanTokenRevalidator.class);

    private final DhanTokenManager tokenManager;
    private final DhanAuthClient authClient;
    private final DhanConnectionSettings settings;
    private final long intervalMs;
    private final ScheduledExecutorService executor;
    private final AtomicLong lastRunAtMs = new AtomicLong(0L);
    private volatile boolean shutdown;

    public DhanTokenRevalidator(
            DhanTokenManager tokenManager,
            DhanAuthClient authClient,
            DhanConnectionSettings settings
    ) {
        this(tokenManager, authClient, settings, DhanProtocolConstants.TOKEN_REVALIDATION_INTERVAL_MS);
    }

    DhanTokenRevalidator(
            DhanTokenManager tokenManager,
            DhanAuthClient authClient,
            DhanConnectionSettings settings,
            long intervalMs
    ) {
        this.tokenManager = tokenManager;
        this.authClient = authClient;
        this.settings = settings;
        this.intervalMs = intervalMs;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "dhan-token-revalidator");
            thread.setDaemon(true);
            return thread;
        });
    }

    public long intervalMs() {
        return intervalMs;
    }

    public void start() {
        if (shutdown) {
            log.warn("DhanTokenRevalidator.start() called after shutdown — ignoring");
            return;
        }
        executor.scheduleAtFixedRate(
                this::runSafely,
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
        log.info("DhanTokenRevalidator started — interval={}ms", intervalMs);
    }

    public void stop() {
        shutdown = true;
        executor.shutdown();
        log.info("DhanTokenRevalidator stopped");
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Visible for tests: triggers a single revalidation cycle. Returns
     * {@code true} if the cached expiry was updated.
     */
    public boolean runOnce() {
        return revalidateNow();
    }

    /** Visible for tests: timestamp of the last successful revalidation start. */
    public long lastRunAtMs() {
        return lastRunAtMs.get();
    }

    private void runSafely() {
        try {
            revalidateNow();
        } catch (RuntimeException ex) {
            log.warn("DhanTokenRevalidator cycle failed: {}", ex.getMessage());
        }
    }

    private boolean revalidateNow() {
        lastRunAtMs.set(System.currentTimeMillis());
        DhanTokenState state = tokenManager.currentSnapshot();
        if (state == null || state.accessToken() == null || state.accessToken().isBlank()) {
            return false;
        }
        try {
            DhanTokenInfo info = authClient.fetchProfile(state.accessToken(), settings.refreshBufferMillis());
            if (info.valid() && !info.refreshRecommended()) {
                boolean updated = tokenManager.updateCachedExpiry(info.expiryEpochMs());
                if (updated) {
                    log.info(
                            "Dhan token revalidated by /v2/profile: cached expiry now {}",
                            java.time.Instant.ofEpochMilli(info.expiryEpochMs())
                    );
                }
                return updated;
            }
            // Broker says: not valid, or refresh recommended.
            //
            // IMPORTANT: do NOT call tokenManager.invalidate() here.
            // The revalidator runs every 5 min; a /v2/profile response
            // that flags `refreshRecommended = true` is normal (it
            // happens at expiry-window), and a *transient* network
            // error that causes `valid = false` would otherwise wipe
            // the cache. Instead, the next ensureValid() (driven by a
            // real order) will see the buffer has elapsed and mint
            // fresh, gated by the throttle.
            //
            // The one path that DOES invalidate is the caught
            // DhanAuthenticationException below — that's a confirmed
            // 401/403 from the broker, which means the token is dead
            // and no amount of waiting will revive it.
            log.info(
                    "Dhan /v2/profile flagged token for refresh (valid={}, refreshRecommended={}); "
                            + "leaving cache intact — next ensureValid() will mint under the shared throttle",
                    info.valid(), info.refreshRecommended());
            return false;
        } catch (DhanAuthenticationException ex) {
            // Confirmed auth rejection: 401 / 403. The token is dead
            // and no amount of waiting will revive it. Invalidate so
            // the next ensureValid() mints a fresh TOTP.
            log.warn("Dhan /v2/profile rejected cached token ({}): invalidating", ex.getMessage());
            tokenManager.invalidate();
            return false;
        } catch (DhanBrokerException ex) {
            // Transient / 5xx / rate-limit. Do NOT invalidate — the
            // token is most likely still valid; only the profile
            // endpoint failed. Wiping the cache on a transient
            // network error was the historical "5 minutes → 3 mints"
            // bug.
            log.warn(
                    "Dhan /v2/profile transient broker error ({}); keeping cached token — next "
                            + "ensureValid() will retry the profile call",
                    ex.getMessage());
            return false;
        } catch (RuntimeException ex) {
            // Unknown — log and keep cached token.
            log.debug("Dhan /v2/profile unexpected error: {}", ex.getMessage());
            return false;
        }
    }
}
