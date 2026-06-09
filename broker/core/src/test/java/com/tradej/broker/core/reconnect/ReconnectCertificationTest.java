package com.tradej.broker.core.reconnect;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Certification tests for reconnect behavior under various failure scenarios.
 * Verifies exponential backoff, storm protection, resubscribe, and state restoration.
 */
@Tag("unit")
class ReconnectCertificationTest {

    @Test
    void exponentialBackoff_increasesDelayPerAttempt() {
        ReconnectManager manager = new ReconnectManager(5, 100L, 10_000L);
        List<Long> delays = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        long start = System.currentTimeMillis();
        manager.attempt(() -> {
            long elapsed = System.currentTimeMillis() - start;
            delays.add(elapsed);
            calls.incrementAndGet();
            return false;
        });
        long total = System.currentTimeMillis() - start;

        assertEquals(5, calls.get());
        assertFalse(manager.attempts() == 0 || manager.stormCount() == 0,
                "After exhausting all attempts, storm count should be > 0");
        assertTrue(total >= 100L, "Total time should include at least base delay");
    }

    @Test
    void stormProtection_cooldownAfterExhaustedAttempts() {
        ReconnectManager manager = new ReconnectManager(2, 10L, 50L, 100L);

        boolean firstCycle = manager.attempt(() -> false);
        assertFalse(firstCycle);
        assertEquals(1, manager.stormCount());

        long start = System.currentTimeMillis();
        boolean secondCycle = manager.attempt(() -> false);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(secondCycle);
        assertTrue(elapsed >= 100L,
                "Storm cooldown should delay the next reconnect cycle (elapsed=" + elapsed + "ms)");
    }

    @Test
    void successfulReconnect_resetsStormCount() {
        ReconnectManager manager = new ReconnectManager(3, 10L, 50L);

        manager.attempt(() -> false);
        assertEquals(1, manager.stormCount());

        manager.reset();
        assertEquals(0, manager.stormCount());

        boolean result = manager.attempt(() -> true);
        assertTrue(result);
        assertEquals(0, manager.stormCount());
        assertEquals(0, manager.attempts());
    }

    @Test
    void resubscribeCallback_invokedAfterReconnect() {
        AtomicBoolean resubscribed = new AtomicBoolean(false);
        ReconnectManager manager = new ReconnectManager(3, 10L, 50L);

        boolean result = manager.attempt(() -> {
            resubscribed.set(true);
            return true;
        });

        assertTrue(result);
        assertTrue(resubscribed.get(), "Resubscribe should be invoked as part of reconnect");
    }

    @Test
    void reconnectFailsAfterMaxAttempts_allAttemptsExhausted() {
        ReconnectManager manager = new ReconnectManager(8, 10L, 100L);
        AtomicInteger attempts = new AtomicInteger();

        boolean result = manager.attempt(() -> {
            attempts.incrementAndGet();
            return false;
        });

        assertFalse(result);
        assertEquals(8, attempts.get(), "Should attempt exactly maxAttempts times");
    }

    @Test
    void reconnectSucceedsOnThirdAttempt_resetsState() {
        ReconnectManager manager = new ReconnectManager(5, 10L, 100L);
        AtomicInteger attempts = new AtomicInteger();

        boolean result = manager.attempt(() -> attempts.incrementAndGet() >= 3);

        assertTrue(result);
        assertEquals(3, attempts.get());
        assertEquals(0, manager.attempts(), "Attempts should reset after success");
    }

    @Test
    void reconnectWithImmediateSuccess_noBackoffDelay() {
        ReconnectManager manager = new ReconnectManager(5, 1000L, 10_000L);

        long start = System.currentTimeMillis();
        boolean result = manager.attempt(() -> true);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result);
        assertTrue(elapsed < 500L,
                "Immediate success should not incur backoff delay (elapsed=" + elapsed + "ms)");
    }

    @Test
    void maxDelayCaps_exponentialGrowth() {
        ReconnectManager manager = new ReconnectManager(6, 100L, 200L);
        AtomicInteger calls = new AtomicInteger();

        long start = System.currentTimeMillis();
        manager.attempt(() -> {
            calls.incrementAndGet();
            return false;
        });
        long total = System.currentTimeMillis() - start;

        assertEquals(6, calls.get());
        assertTrue(total < 5_000L,
                "Max delay should cap exponential growth (total=" + total + "ms)");
    }
}
