package com.tradej.broker.core.resilience;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with jitter computation.
 * <p>
 * Formula:
 * <pre>{@code
 * delay = min(maxDelayMs, baseDelayMs * 2^(attempt - 1)) + jitter
 * jitter = random(0, delay / 4)
 * }</pre>
 * <p>
 * Thread-safe. Uses {@link ThreadLocalRandom} for contention-free jitter.
 */
public final class BackoffStrategy {
    private BackoffStrategy() {
    }

    /**
     * Computes a backoff delay for the given attempt number.
     *
     * @param attempt     the current attempt (1-based)
     * @param baseDelayMs base delay in milliseconds
     * @param maxDelayMs  maximum delay cap in milliseconds
     * @return backoff delay in milliseconds (deterministic exponential + random jitter)
     */
    public static long computeDelayMs(int attempt, long baseDelayMs, long maxDelayMs) {
        long exponential = Math.min(maxDelayMs, baseDelayMs * (1L << (attempt - 1)));
        long jitter = ThreadLocalRandom.current().nextLong(exponential / 4L + 1L);
        return exponential + jitter;
    }
}
