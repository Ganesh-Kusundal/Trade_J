package com.tradej.broker.core.resilience;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CircuitBreakerTest {

    @Test
    void circuitStartsClosedForNewOperation() {
        CircuitBreaker breaker = new CircuitBreaker();
        assertDoesNotThrow(() -> breaker.assertCanExecute("new-op"));
        assertFalse(breaker.isOpen("new-op"));
    }

    @Test
    void circuitOpensAfterThresholdFailures() {
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerConfig(3, 60_000L, 1));
        breaker.onFailure("op");
        assertFalse(breaker.isOpen("op"));
        breaker.onFailure("op");
        assertFalse(breaker.isOpen("op"));
        breaker.onFailure("op");
        assertTrue(breaker.isOpen("op"));
    }

    @Test
    void circuitRejectsWhileOpen() {
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerConfig(1, 60_000L, 1));
        breaker.onFailure("op");
        assertThrows(IllegalStateException.class, () -> breaker.assertCanExecute("op"));
    }

    @Test
    void circuitHalfOpenAfterTimeout() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerConfig(1, 1_000L, 1));
        breaker.onFailure("op");
        assertTrue(breaker.isOpen("op"));
        Thread.sleep(1_100);
        assertDoesNotThrow(() -> breaker.assertCanExecute("op"));
    }

    @Test
    void circuitClosesOnSuccess() {
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerConfig(1, 60_000L, 1));
        breaker.onFailure("op");
        assertTrue(breaker.isOpen("op"));
        breaker.onSuccess("op");
        assertFalse(breaker.isOpen("op"));
        assertDoesNotThrow(() -> breaker.assertCanExecute("op"));
    }

    @Test
    void perOperationIsolation() {
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerConfig(1, 60_000L, 1));
        breaker.onFailure("op-a");
        assertTrue(breaker.isOpen("op-a"));
        assertFalse(breaker.isOpen("op-b"));
        assertThrows(IllegalStateException.class, () -> breaker.assertCanExecute("op-a"));
        assertDoesNotThrow(() -> breaker.assertCanExecute("op-b"));
    }

    @Test
    void successResetsFailureCount() {
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerConfig(3, 60_000L, 1));
        breaker.onFailure("op");
        breaker.onFailure("op");
        breaker.onSuccess("op");
        breaker.onFailure("op");
        breaker.onFailure("op");
        assertFalse(breaker.isOpen("op"));
    }

    @Test
    void unknownOperationIsNeverOpen() {
        CircuitBreaker breaker = new CircuitBreaker();
        assertFalse(breaker.isOpen("never-seen"));
        assertDoesNotThrow(() -> breaker.assertCanExecute("never-seen"));
    }

    @Test
    void defaultConfigIsApplied() {
        CircuitBreaker breaker = new CircuitBreaker();
        assertEquals(CircuitBreakerConfig.DEFAULT, breaker.config());
    }

    @Test
    void aggressiveConfigOpensFaster() {
        CircuitBreaker aggressive = new CircuitBreaker(CircuitBreakerConfig.AGGRESSIVE);
        assertEquals(3, aggressive.config().failureThreshold());
        aggressive.onFailure("op");
        aggressive.onFailure("op");
        aggressive.onFailure("op");
        assertTrue(aggressive.isOpen("op"));
    }

    @Test
    void conservativeConfigToleratesMoreFailures() {
        CircuitBreaker conservative = new CircuitBreaker(CircuitBreakerConfig.CONSERVATIVE);
        assertEquals(10, conservative.config().failureThreshold());
        for (int i = 0; i < 9; i++) {
            conservative.onFailure("op");
        }
        assertFalse(conservative.isOpen("op"));
        conservative.onFailure("op");
        assertTrue(conservative.isOpen("op"));
    }

    @Test
    void onFailureWithParamsUsesConfigValues() {
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerConfig(2, 60_000L, 1));
        // Even though we pass threshold=100, config threshold=2 is used
        breaker.onFailure("op", 100, 1000L);
        assertFalse(breaker.isOpen("op"));
        breaker.onFailure("op", 100, 1000L);
        assertTrue(breaker.isOpen("op"));
    }
}
