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
}
