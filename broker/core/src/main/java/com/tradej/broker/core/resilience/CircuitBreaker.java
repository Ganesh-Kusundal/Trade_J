package com.tradej.broker.core.resilience;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-operation circuit breaker.
 * <p>
 * Thread-safe. Each operation name gets its own circuit state.
 * After {@code circuitBreakAfterFailures} consecutive failures, the circuit
 * opens for {@code circuitOpenMs} milliseconds before allowing a probe attempt.
 */
public final class CircuitBreaker {

    private final ConcurrentMap<String, CircuitState> circuits = new ConcurrentHashMap<>();

    /**
     * Asserts that the circuit for the given operation is closed.
     *
     * @throws IllegalStateException if the circuit is open
     */
    public void assertCanExecute(String operation) {
        CircuitState state = circuits.get(operation);
        if (state != null) {
            state.assertCanExecute(operation);
        }
    }

    /** Records a successful execution for the given operation — resets failure count. */
    public void onSuccess(String operation) {
        CircuitState state = circuits.get(operation);
        if (state != null) {
            state.onSuccess();
        }
    }

    /** Records a failure for the given operation — may open the circuit. */
    public void onFailure(String operation, int threshold, long openMs) {
        CircuitState state = circuits.computeIfAbsent(operation, ignored -> new CircuitState());
        state.onFailure(threshold, openMs);
    }

    /** Returns {@code true} if the circuit for the given operation is currently open. */
    public boolean isOpen(String operation) {
        CircuitState state = circuits.get(operation);
        if (state == null) {
            return false;
        }
        return state.isOpen();
    }

    static final class CircuitState {
        private int consecutiveFailures;
        private long openUntilEpochMs;

        synchronized void assertCanExecute(String operation) {
            long now = System.currentTimeMillis();
            if (openUntilEpochMs > now) {
                throw new IllegalStateException(
                        "Circuit is open for operation " + operation + " until " + openUntilEpochMs);
            }
            if (openUntilEpochMs != 0L && now >= openUntilEpochMs) {
                openUntilEpochMs = 0L;
                consecutiveFailures = 0;
            }
        }

        synchronized void onSuccess() {
            consecutiveFailures = 0;
            openUntilEpochMs = 0L;
        }

        synchronized void onFailure(int threshold, long openMs) {
            consecutiveFailures++;
            if (consecutiveFailures >= threshold) {
                openUntilEpochMs = System.currentTimeMillis() + openMs;
            }
        }

        synchronized boolean isOpen() {
            return openUntilEpochMs > System.currentTimeMillis();
        }
    }
}
