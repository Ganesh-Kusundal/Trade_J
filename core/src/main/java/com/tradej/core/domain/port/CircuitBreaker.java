package com.tradej.core.domain.port;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Circuit breaker for protecting against cascading failures from
 * external dependencies (broker connections, market data feeds).
 *
 * <p>States: CLOSED (normal) → OPEN (failing) → HALF_OPEN (testing recovery)
 */
public interface CircuitBreaker {

    enum State { CLOSED, OPEN, HALF_OPEN }

    /**
     * Execute a supplier within the circuit breaker protection.
     * @throws CircuitBreakerOpenException if the circuit is open
     */
    <T> T execute(Supplier<T> supplier);

    /**
     * Execute a runnable within the circuit breaker protection.
     */
    void execute(Runnable runnable);

    State state();

    int failureCount();

    int successCount();

    void reset();

    /**
     * Configuration for circuit breaker behavior.
     */
    record Config(
            int failureThreshold,
            Duration openDuration,
            int halfOpenMaxAttempts
    ) {
        public Config {
            if (failureThreshold <= 0) failureThreshold = 5;
            if (openDuration == null) openDuration = Duration.ofSeconds(30);
            if (halfOpenMaxAttempts <= 0) halfOpenMaxAttempts = 3;
        }

        public static Config defaults() {
            return new Config(5, Duration.ofSeconds(30), 3);
        }
    }

    class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String name) {
            super("Circuit breaker '" + name + "' is OPEN");
        }
    }
}
