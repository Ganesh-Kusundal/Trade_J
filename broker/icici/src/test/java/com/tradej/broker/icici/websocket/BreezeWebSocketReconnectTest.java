package com.tradej.broker.icici.websocket;

import com.tradej.broker.core.reconnect.ReconnectManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BreezeWebSocketReconnectTest {

    @Test
    void disconnectTriggersReconnect() {
        ReconnectManager manager = new ReconnectManager(3, 10L, 100L);
        AtomicInteger reconnectAttempts = new AtomicInteger();
        boolean result = manager.attempt(() -> {
            reconnectAttempts.incrementAndGet();
            return true;
        });
        assertTrue(result);
        assertEquals(1, reconnectAttempts.get());
    }

    @Test
    void reconnectBackoffIncreases() {
        ReconnectManager manager = new ReconnectManager(3, 10L, 1000L);
        long start = System.nanoTime();
        manager.attempt(() -> false);
        long elapsed = System.nanoTime() - start;
        assertTrue(elapsed >= 25_000_000L, "Backoff should cause delays, actual: " + elapsed / 1_000_000 + "ms");
    }

    @Test
    void reconnectRestoresAfterFailure() {
        ReconnectManager manager = new ReconnectManager(5, 10L, 100L);
        AtomicInteger calls = new AtomicInteger();
        boolean result = manager.attempt(() -> calls.incrementAndGet() >= 3);
        assertTrue(result);
        assertEquals(3, calls.get());
        assertEquals(0, manager.attempts());
    }

    @Test
    void reconnectExhaustsMaxAttempts() {
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
    void stormProtectionAfterExhaustedAttempts() {
        ReconnectManager manager = new ReconnectManager(2, 10L, 50L, 100L);
        manager.attempt(() -> false);
        assertEquals(1, manager.stormCount());
        manager.reset();
        assertEquals(0, manager.stormCount());
    }
}
