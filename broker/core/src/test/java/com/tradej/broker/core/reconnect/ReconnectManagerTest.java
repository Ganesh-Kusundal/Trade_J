package com.tradej.broker.core.reconnect;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ReconnectManagerTest {

    @Test
    void successfulReconnectReturnsTrue() {
        ReconnectManager manager = new ReconnectManager(3, 10L, 100L);
        boolean result = manager.attempt(() -> true);
        assertTrue(result);
        assertEquals(0, manager.attempts());
    }

    @Test
    void failedReconnectReturnsFalseAfterMaxAttempts() {
        ReconnectManager manager = new ReconnectManager(3, 10L, 100L);
        AtomicInteger calls = new AtomicInteger();
        boolean result = manager.attempt(() -> {
            calls.incrementAndGet();
            return false;
        });
        assertFalse(result);
        assertEquals(3, calls.get());
    }

    @Test
    void reconnectRetriesUntilSuccess() {
        ReconnectManager manager = new ReconnectManager(5, 10L, 100L);
        AtomicInteger calls = new AtomicInteger();
        boolean result = manager.attempt(() -> calls.incrementAndGet() >= 3);
        assertTrue(result);
        assertEquals(3, calls.get());
        assertEquals(0, manager.attempts());
    }

    @Test
    void resetClearsAttemptCount() {
        ReconnectManager manager = new ReconnectManager(1, 10L, 100L);
        manager.attempt(() -> false);
        assertEquals(1, manager.attempts());
        manager.reset();
        assertEquals(0, manager.attempts());
    }

    @Test
    void invalidMaxAttemptsThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReconnectManager(0, 10L, 100L));
    }

    @Test
    void invalidDelayBoundsThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReconnectManager(3, 0L, 100L));
        assertThrows(IllegalArgumentException.class,
                () -> new ReconnectManager(3, 200L, 100L));
    }
}
