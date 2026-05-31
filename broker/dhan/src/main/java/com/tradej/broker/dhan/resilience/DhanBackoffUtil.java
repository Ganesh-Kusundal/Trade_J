package com.tradej.broker.dhan.resilience;

import com.tradej.broker.core.resilience.BackoffStrategy;

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
 *
 * @deprecated Use {@link BackoffStrategy} from {@code trade-broker-core} instead.
 * This class now delegates to it and will be removed after all call sites are updated.
 */
@Deprecated
public final class DhanBackoffUtil {
    private DhanBackoffUtil() {
    }

    /**
     * Computes a backoff delay for the given attempt number.
     *
     * @deprecated use {@link BackoffStrategy#computeDelayMs(int, long, long)}
     */
    @Deprecated
    public static long computeDelayMs(int attempt, long baseDelayMs, long maxDelayMs) {
        return BackoffStrategy.computeDelayMs(attempt, baseDelayMs, maxDelayMs);
    }
}
