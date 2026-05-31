package com.tradej.execution.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class TradingCircuitBreakerUnitTest {
    @Test
    void opensAfterConfiguredFailureThresholdAndRecoversAfterCooldown() throws Exception {
        TradingCircuitBreaker circuitBreaker = new TradingCircuitBreaker(2, 50L);

        assertTrue(circuitBreaker.allowsRequest());

        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.allowsRequest());

        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.isOpen());
        assertFalse(circuitBreaker.allowsRequest());

        Thread.sleep(75L);

        assertTrue(circuitBreaker.allowsRequest());
    }

    @Test
    void testCircuitBreakerConcurrencyRescue() throws Exception {
        // Threshold=2 failures, Cooldown=50ms, MaxProbes=3
        TradingCircuitBreaker circuitBreaker = new TradingCircuitBreaker(2, 50L, 3);

        // 1. Trip the circuit to OPEN
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.isOpen());

        // 2. Wait for cooldown window to expire
        Thread.sleep(60L);

        // 3. allowsRequest transitions state from OPEN to HALF_OPEN, setting halfOpenProbes = 1
        assertTrue(circuitBreaker.allowsRequest());
        assertTrue(circuitBreaker.currentState() == TradingCircuitBreaker.State.HALF_OPEN);

        // 4. A concurrent probe fails and immediately trips the circuit to OPEN
        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.isOpen());

        // 5. A successful probe completes and rescues the circuit to CLOSED
        circuitBreaker.recordSuccess();
        assertFalse(circuitBreaker.isOpen());
        assertTrue(circuitBreaker.currentState() == TradingCircuitBreaker.State.CLOSED);
        assertTrue(circuitBreaker.allowsRequest());
    }
}
