package com.tradej.broker.core.resilience;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CircuitBreakerMetricsTest {

    @Test
    void noop_doesNotThrow() {
        CircuitBreakerMetrics noop = CircuitBreakerMetrics.NOOP;
        assertDoesNotThrow(() -> {
            noop.recordStateChange("dhan", "CLOSED", "OPEN");
            noop.recordExecution("dhan", "place_order", 42L, true);
        });
    }

    @Test
    void metricsCallback_invokedOnStateChange() {
        List<String> captured = new ArrayList<>();
        CircuitBreakerMetrics recording = new CircuitBreakerMetrics() {
            @Override
            public void recordStateChange(String broker, String fromState, String toState) {
                captured.add(broker + ":" + fromState + "->" + toState);
            }

            @Override
            public void recordExecution(String broker, String operation, long durationMs, boolean success) {}
        };

        CircuitBreaker breaker = new CircuitBreaker(
                new CircuitBreakerConfig(2, 60_000L, 1), recording);

        breaker.onFailure("dhan:place_order");
        breaker.onFailure("dhan:place_order");

        assertEquals(1, captured.size());
        assertEquals("dhan:CLOSED->OPEN", captured.get(0));
    }

    @Test
    void metricsCallback_invokedOnSuccessRecovery() {
        List<String> captured = new ArrayList<>();
        CircuitBreakerMetrics recording = new CircuitBreakerMetrics() {
            @Override
            public void recordStateChange(String broker, String fromState, String toState) {
                captured.add(broker + ":" + fromState + "->" + toState);
            }

            @Override
            public void recordExecution(String broker, String operation, long durationMs, boolean success) {}
        };

        CircuitBreaker breaker = new CircuitBreaker(
                new CircuitBreakerConfig(1, 60_000L, 1), recording);

        breaker.onFailure("upstox:cancel_order");
        breaker.onSuccess("upstox:cancel_order");

        assertEquals(2, captured.size());
        assertEquals("upstox:CLOSED->OPEN", captured.get(0));
        assertEquals("upstox:OPEN->CLOSED", captured.get(1));
    }

    @Test
    void metricsCallback_invokedOnAssertCanExecute() {
        List<Object[]> captured = new ArrayList<>();
        CircuitBreakerMetrics recording = new CircuitBreakerMetrics() {
            @Override
            public void recordStateChange(String broker, String fromState, String toState) {}

            @Override
            public void recordExecution(String broker, String operation, long durationMs, boolean success) {
                captured.add(new Object[]{broker, operation, durationMs, success});
            }
        };

        CircuitBreaker breaker = new CircuitBreaker(
                new CircuitBreakerConfig(5, 60_000L, 1), recording);

        // Record a failure first to create the circuit state, then assert can execute
        breaker.onFailure("icici:get_quotes");
        breaker.assertCanExecute("icici:get_quotes");

        assertEquals(1, captured.size());
        assertEquals("icici", captured.get(0)[0]);
        assertEquals("icici:get_quotes", captured.get(0)[1]);
        assertTrue((boolean) captured.get(0)[3]);
    }

    @Test
    void metricsCallback_executionFailure_onOpenCircuit() {
        List<Object[]> captured = new ArrayList<>();
        CircuitBreakerMetrics recording = new CircuitBreakerMetrics() {
            @Override
            public void recordStateChange(String broker, String fromState, String toState) {}

            @Override
            public void recordExecution(String broker, String operation, long durationMs, boolean success) {
                captured.add(new Object[]{broker, operation, success});
            }
        };

        CircuitBreaker breaker = new CircuitBreaker(
                new CircuitBreakerConfig(1, 60_000L, 1), recording);

        breaker.onFailure("dhan:place_order");
        assertThrows(IllegalStateException.class, () -> breaker.assertCanExecute("dhan:place_order"));

        assertEquals(1, captured.size());
        assertEquals("dhan:place_order", captured.get(0)[1]);
        assertFalse((boolean) captured.get(0)[2]);
    }

    @Test
    void noMetrics_constructorStillWorks() {
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerConfig(2, 60_000L, 1));
        assertDoesNotThrow(() -> {
            breaker.onFailure("op");
            breaker.onFailure("op");
            assertTrue(breaker.isOpen("op"));
        });
    }

    @Test
    void metricsCallback_notInvokedOnSkip() {
        List<String> stateChanges = new ArrayList<>();
        CircuitBreakerMetrics recording = new CircuitBreakerMetrics() {
            @Override
            public void recordStateChange(String broker, String fromState, String toState) {
                stateChanges.add(broker + ":" + fromState + "->" + toState);
            }

            @Override
            public void recordExecution(String broker, String operation, long durationMs, boolean success) {}
        };

        // Use threshold=3 so the circuit opens after 3 failures
        CircuitBreaker breaker = new CircuitBreaker(
                new CircuitBreakerConfig(3, 60_000L, 1), recording);

        // No failures yet, circuit is closed — success should not emit state change
        breaker.onSuccess("never-failed-op");
        assertTrue(stateChanges.isEmpty());

        // First failure below threshold — no state change to OPEN
        breaker.onFailure("op");
        assertTrue(stateChanges.isEmpty());

        // Second failure below threshold
        breaker.onFailure("op");
        assertTrue(stateChanges.isEmpty());

        // Third failure hits threshold — now OPEN
        breaker.onFailure("op");
        assertEquals(1, stateChanges.size());
        assertEquals("op:CLOSED->OPEN", stateChanges.get(0));
    }
}
