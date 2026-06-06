package com.tradej.broker.upstox.websocket;

import com.tradej.broker.core.reconnect.ReconnectManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxWebSocketReconnectTest {

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
        // With backoff: attempt1(10ms) + attempt2(20ms) + attempt3 = at least 30ms
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
    void resetClearsReconnectState() {
        ReconnectManager manager = new ReconnectManager(2, 10L, 100L);
        manager.attempt(() -> false);
        assertTrue(manager.attempts() > 0 || manager.stormCount() > 0);
        manager.reset();
        assertEquals(0, manager.attempts());
        assertEquals(0, manager.stormCount());
    }
}
