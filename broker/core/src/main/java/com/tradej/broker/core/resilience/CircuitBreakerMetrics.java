package com.tradej.broker.core.resilience;

/**
 * Callback interface for circuit breaker lifecycle events.
 * Implementations record metrics or logging on state transitions and executions.
 */
public interface CircuitBreakerMetrics {

    CircuitBreakerMetrics NOOP = new CircuitBreakerMetrics() {
        @Override public void recordStateChange(String broker, String fromState, String toState) {}
        @Override public void recordExecution(String broker, String operation, long durationMs, boolean success) {}
    };

    void recordStateChange(String broker, String fromState, String toState);

    void recordExecution(String broker, String operation, long durationMs, boolean success);
}
