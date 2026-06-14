package com.tradej.broker.api.auth;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cross-broker session/token-acquisition throttle.
 *
 * <p>Solves the "we minted a new session token 5 times in 5 minutes"
 * failure mode. Three rules:
 *
 * <ol>
 *   <li><b>Cooldown</b>: every broker mints a fresh token through
 *       {@link #tryAcquire(String)} which returns {@code false} if
 *       less than {@code baseCooldownMs} elapsed since the last mint.
 *       Default 5 minutes — long enough to ride out TOTP-rotation
 *       races, short enough that a legitimately stuck token is still
 *       recoverable within a single trading session.</li>
 *   <li><b>Exponential backoff</b>: each consecutive failure doubles
 *       the cooldown, capped at {@code maxCooldownMs}. This stops
 *       infinite loops where the broker keeps rejecting newly-minted
 *       tokens and the platform keeps re-trying.</li>
 *   <li><b>Success resets</b>: a successful acquire (or a successful
 *       use of the minted token) resets the failure counter.</li>
 * </ol>
 *
 * <p>The cooldown timestamp is per-token-source id — e.g. {@code "icici-breeze"},
 * {@code "dhan-totp"}, {@code "upstox-oauth"}. Different sources do not
 * throttle each other.
 *
 * <p>State is process-local by design. Restarting the JVM resets the
 * backoff — that's an acceptable trade-off for an internal broker
 * token-mint path.
 */
public final class TokenAcquisitionThrottle {

    public static final long DEFAULT_BASE_COOLDOWN_MS = 5L * 60_000L;       // 5 min
    public static final long DEFAULT_MAX_COOLDOWN_MS  = 30L * 60_000L;      // 30 min

    private final long baseCooldownMs;
    private final long maxCooldownMs;
    private final Clock clock;

    private final AtomicLong lastAcquireAtMs = new AtomicLong(0L);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public TokenAcquisitionThrottle() {
        this(DEFAULT_BASE_COOLDOWN_MS, DEFAULT_MAX_COOLDOWN_MS, Clock.systemUTC());
    }

    public TokenAcquisitionThrottle(long baseCooldownMs, long maxCooldownMs, Clock clock) {
        if (baseCooldownMs < 0) {
            throw new IllegalArgumentException("baseCooldownMs must be >= 0");
        }
        if (maxCooldownMs < baseCooldownMs) {
            throw new IllegalArgumentException("maxCooldownMs must be >= baseCooldownMs");
        }
        this.baseCooldownMs = baseCooldownMs;
        this.maxCooldownMs = maxCooldownMs;
        this.clock = clock;
    }

    /**
     * Result of a {@link #tryAcquire(String)} check.
     *
     * @param allowed            true if the caller may proceed with a mint
     * @param retryAfterMs       the number of milliseconds to wait before
     *                            a successful acquire is possible. 0 if
     *                            {@code allowed} is true.
     * @param currentCooldownMs  the cooldown that was applied (after
     *                            backoff)
     */
    public record AcquireResult(boolean allowed, long retryAfterMs, long currentCooldownMs) {}

    /**
     * Check whether the caller may proceed with a token mint. Records
     * a successful acquire (which resets the failure counter) on the
     * first call after the cooldown elapsed.
     *
     * @param sourceId identifier for the token source (e.g. "icici-breeze")
     */
    public AcquireResult tryAcquire(String sourceId) {
        long now = clock.millis();
        long last = lastAcquireAtMs.get();
        long cooldown = currentCooldownMs();
        if (last > 0 && now - last < cooldown) {
            return new AcquireResult(false, cooldown - (now - last), cooldown);
        }
        lastAcquireAtMs.set(now);
        consecutiveFailures.set(0);
        return new AcquireResult(true, 0L, cooldown);
    }

    /**
     * Record a failure. The next {@link #tryAcquire(String)} will be
     * subject to a doubled cooldown, capped at {@code maxCooldownMs}.
     */
    public void recordFailure() {
        consecutiveFailures.incrementAndGet();
    }

    /**
     * Explicitly clear the failure counter (e.g. after a manual token
     * reset from the operator CLI).
     */
    public void reset() {
        consecutiveFailures.set(0);
        lastAcquireAtMs.set(0L);
    }

    /** Visible for tests: the cooldown that would apply to the next acquire. */
    public long currentCooldownMs() {
        int failures = consecutiveFailures.get();
        if (failures == 0) {
            return baseCooldownMs;
        }
        long multiplied = baseCooldownMs << Math.min(failures, 30);
        return Math.min(multiplied, maxCooldownMs);
    }

    /** Visible for tests. */
    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }
}
