package com.tradej.broker.dhan.resilience;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared exponential-backoff-with-jitter calculation used by both REST retry
 * ({@link DhanResilienceExecutor}) and WebSocket reconnection
 * ({@link com.tradej.broker.dhan.websocket.DhanWebSocketMultiplexer}).
 *
 * <p>The formula is:
 * <pre>{@code
 * delay = min(maxDelayMs, baseDelayMs * 2^(attempt - 1)) + jitter
 * jitter = random(0, delay / 4)
 * }</pre>
 */
public final class DhanBackoffUtil {
    private DhanBackoffUtil() {
    }

    /**
     * Computes a backoff delay for the given attempt number.
     *
     * @param attempt    the current attempt (1-based)
     * @param baseDelayMs  base delay in milliseconds
     * @param maxDelayMs   maximum delay cap in milliseconds
     * @return backoff delay in milliseconds (deterministic exponential + random jitter)
     */
    public static long computeDelayMs(int attempt, long baseDelayMs, long maxDelayMs) {
        long exponential = Math.min(maxDelayMs, baseDelayMs * (1L << (attempt - 1)));
        long jitter = ThreadLocalRandom.current().nextLong(exponential / 4L + 1L);
        return exponential + jitter;
    }
}
