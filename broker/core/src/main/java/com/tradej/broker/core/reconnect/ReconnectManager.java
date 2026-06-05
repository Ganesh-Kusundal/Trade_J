package com.tradej.broker.core.reconnect;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Exponential backoff helper for WebSocket reconnect attempts.
 */
public final class ReconnectManager {

    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final AtomicInteger attempts = new AtomicInteger();

    public ReconnectManager(int maxAttempts, long baseDelayMs, long maxDelayMs) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (baseDelayMs <= 0 || maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("Invalid reconnect delay bounds");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    public boolean attempt(BooleanSupplier reconnectAction) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            attempts.set(attempt);
            if (reconnectAction.getAsBoolean()) {
                attempts.set(0);
                return true;
            }
            if (attempt < maxAttempts) {
                sleep(backoffDelayMs(attempt));
            }
        }
        return false;
    }

    public int attempts() {
        return attempts.get();
    }

    public void reset() {
        attempts.set(0);
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
