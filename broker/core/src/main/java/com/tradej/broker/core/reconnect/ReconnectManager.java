package com.tradej.broker.core.reconnect;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Exponential backoff helper for WebSocket reconnect attempts with storm protection.
 *
 * <p>Storm protection: after exhausting all reconnect attempts without success,
 * a cooldown period is enforced before the next reconnect cycle can begin.
 * The cooldown increases linearly with consecutive storm cycles to prevent
 * hammering the broker during extended outages.
 */
public final class ReconnectManager {

    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final long stormCooldownMs;
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger stormCount = new AtomicInteger();

    public ReconnectManager(int maxAttempts, long baseDelayMs, long maxDelayMs) {
        this(maxAttempts, baseDelayMs, maxDelayMs, 60_000L);
    }

    public ReconnectManager(int maxAttempts, long baseDelayMs, long maxDelayMs, long stormCooldownMs) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (baseDelayMs <= 0 || maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("Invalid reconnect delay bounds");
        }
        if (stormCooldownMs < 0) {
            throw new IllegalArgumentException("stormCooldownMs must be >= 0");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.stormCooldownMs = stormCooldownMs;
    }

    public boolean attempt(BooleanSupplier reconnectAction) {
        // Enforce storm cooldown if previous cycle exhausted all attempts
        if (stormCount.get() > 0 && stormCooldownMs > 0) {
            long cooldown = stormCooldownMs * stormCount.get();
            sleep(cooldown);
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            attempts.set(attempt);
            if (reconnectAction.getAsBoolean()) {
                attempts.set(0);
                stormCount.set(0);
                return true;
            }
            if (attempt < maxAttempts) {
                sleep(backoffDelayMs(attempt));
            }
        }
        stormCount.incrementAndGet();
        return false;
    }

    public int attempts() {
        return attempts.get();
    }

    public int stormCount() {
        return stormCount.get();
    }

    public void reset() {
        attempts.set(0);
        stormCount.set(0);
    }

    private long backoffDelayMs(int attempt) {
        long delay = baseDelayMs * (1L << Math.min(attempt - 1, 10));
        return Math.min(delay, maxDelayMs);
    }

    private static void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during reconnect backoff", ex);
        }
    }
}
