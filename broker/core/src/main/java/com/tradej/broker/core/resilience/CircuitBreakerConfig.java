package com.tradej.broker.core.resilience;

/**
 * Per-broker circuit breaker configuration.
 *
 * @param failureThreshold  consecutive failures before opening circuit
 * @param openDurationMs    how long the circuit stays open (ms)
 * @param halfOpenProbes    number of probe requests allowed in half-open state
 */
public record CircuitBreakerConfig(
        int failureThreshold,
        long openDurationMs,
        int halfOpenProbes
) {
    public static final CircuitBreakerConfig DEFAULT = new CircuitBreakerConfig(5, 30_000L, 1);
    public static final CircuitBreakerConfig AGGRESSIVE = new CircuitBreakerConfig(3, 15_000L, 1);
    public static final CircuitBreakerConfig CONSERVATIVE = new CircuitBreakerConfig(10, 60_000L, 1);

    public CircuitBreakerConfig {
        if (failureThreshold < 1) throw new IllegalArgumentException("failureThreshold must be >= 1");
        if (openDurationMs < 1000) throw new IllegalArgumentException("openDurationMs must be >= 1000");
        if (halfOpenProbes < 1) throw new IllegalArgumentException("halfOpenProbes must be >= 1");
    }
}
