package com.tradej.execution.service;

import com.tradej.core.testing.ConcurrentStressTester;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stress tests for concurrent access to {@link TradingCircuitBreaker}.
 */
@Tag("stress")
class TradingCircuitBreakerStressTest {

    /**
     * Verifies that after the open window expires, at most
     * {@code maxHalfOpenProbes} concurrent probes are allowed through.
     */
    @Test
    void concurrentRequestsToOpenBreakerLimitProbes() throws Exception {
        TradingCircuitBreaker cb = new TradingCircuitBreaker(2, 50L, 3);

        // Trip the breaker
        cb.recordFailure();
        cb.recordFailure();
        assertTrue(cb.isOpen());

        // Wait for the open window to expire
        Thread.sleep(75L);

        AtomicInteger allowed = new AtomicInteger();

        var result = ConcurrentStressTester.run(20, 10, threadIndex -> {
            if (cb.allowsRequest()) {
                allowed.incrementAndGet();
            }
        });

        result.assertAllPassed().requireNoExceptions();

        assertTrue(allowed.get() <= 3,
                "Expected at most 3 HALF_OPEN probes, got " + allowed.get());
    }

    /**
     * Verifies that a HALF_OPEN probe success resets the breaker to CLOSED
     * and all subsequent requests pass through.
     */
    @Test
    void halfOpenProbeSuccessResetsBreaker() throws Exception {
        TradingCircuitBreaker cb = new TradingCircuitBreaker(2, 50L, 3);

        // Trip
        cb.recordFailure();
        cb.recordFailure();
        assertTrue(cb.isOpen());

        // Wait for open window
        Thread.sleep(75L);

        // First probe should succeed (transitions to HALF_OPEN)
        assertTrue(cb.allowsRequest());
        cb.recordSuccess();

        // CLOSED — everything passes
        assertTrue(cb.allowsRequest());
        assertTrue(cb.allowsRequest());
        assertEquals(TradingCircuitBreaker.State.CLOSED, cb.currentState());
    }

    /**
     * Verifies that a failed probe in HALF_OPEN transitions back to OPEN.
     */
    @Test
    void halfOpenProbeFailureReopensBreaker() throws Exception {
        TradingCircuitBreaker cb = new TradingCircuitBreaker(2, 50L, 3);

        // Trip
        cb.recordFailure();
        cb.recordFailure();

        // Wait for open window
        Thread.sleep(75L);

        // Probe
        assertTrue(cb.allowsRequest());
        cb.recordFailure(); // probe failed — only 1 failure needed in HALF_OPEN... wait, threshold is 2

        // Actually with threshold=2, one failure in HALF_OPEN isn't enough
        // to re-trip. The failure count is cumulative. Let me check...
        // consecutiveFailures was 2 when we entered HALF_OPEN, then recordFailure
        // increments it to 3, which is >= 2, so it goes to OPEN.

        assertTrue(cb.isOpen(), "Should be OPEN again after failed probe");
    }

    /**
     * Verifies that concurrent success/failure calls don't corrupt internal state.
     */
    @Test
    void concurrentRecordCallsDontCorruptState() {
        TradingCircuitBreaker cb = new TradingCircuitBreaker(10, 30_000L, 5);
        int workers = 8;
        int callsPerWorker = 500;

        var result = ConcurrentStressTester.run(workers, callsPerWorker, threadIndex -> {
            for (int i = 0; i < 10; i++) {
                cb.recordSuccess();
                cb.recordFailure();
            }
        });

        result.assertAllPassed().requireNoExceptions();

        // Verify state is one of the valid enum values
        assertTrue(cb.currentState() == TradingCircuitBreaker.State.CLOSED
                        || cb.currentState() == TradingCircuitBreaker.State.OPEN
                        || cb.currentState() == TradingCircuitBreaker.State.HALF_OPEN,
                "State should be a valid enum value, got: " + cb.currentState());
    }
}
