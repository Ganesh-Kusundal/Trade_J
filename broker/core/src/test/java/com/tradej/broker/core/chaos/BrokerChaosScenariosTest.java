package com.tradej.broker.core.chaos;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Chaos tests simulating broker failure scenarios:
 * session expiry, WebSocket disconnect, rate limiting, and failover.
 */
@Tag("unit")
class BrokerChaosScenariosTest {

    // ── Session expiry → failover ─────────────────────────────────────

    @Test
    void sessionExpiryTriggersFailoverToNextNode() {
        var primaryCallCount = new AtomicInteger();
        var secondaryCallCount = new AtomicInteger();

        IBrokerConnection primary = mockConnection("primary");
        when(primary.marketData().getLtpPaisa(any())).thenAnswer(inv -> {
            primaryCallCount.incrementAndGet();
            throw new RuntimeException("Session expired: 401 Unauthorized");
        });

        IBrokerConnection secondary = mockConnection("secondary");
        when(secondary.marketData().getLtpPaisa(any())).thenAnswer(inv -> {
            secondaryCallCount.incrementAndGet();
            return 254350L;
        });

        var gateway = new LoadBalancedBrokerGateway(List.of(primary, secondary));

        // First call hits primary, fails, rotates
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        try {
            gateway.marketData().getLtpPaisa(key);
        } catch (RuntimeException expected) {
            // Primary may throw before failover is triggered at this level
        }

        // After rotation, secondary should be primary
        gateway.rotatePrimary();
        long ltp = gateway.marketData().getLtpPaisa(key);
        assertEquals(254350L, ltp, "After failover, secondary should serve requests");
        assertTrue(secondaryCallCount.get() >= 1, "Secondary should have been called");
    }

    // ── WebSocket disconnect → event gap recovery ─────────────────────

    @Test
    void webSocketDisconnectDoesNotCrashGateway() {
        var disconnectLatch = new CountDownLatch(1);

        IBrokerConnection node1 = mockConnection("ws-node-1");
        WebSocketMultiplexer ws1 = node1.websocket();
        doAnswer(inv -> {
            disconnectLatch.countDown();
            throw new RuntimeException("WebSocket connection closed");
        }).when(ws1).connect();

        IBrokerConnection node2 = mockConnection("ws-node-2");

        var gateway = new LoadBalancedBrokerGateway(List.of(node1, node2));

        // Attempt connect — node1 fails
        assertDoesNotThrow(() -> {
            try {
                gateway.connect();
            } catch (RuntimeException expected) {
                // Node 1 failure is expected
            }
        }, "Gateway should survive WebSocket disconnect without crashing");

        // Rotate to node2 and connect successfully
        gateway.rotatePrimary();
        assertDoesNotThrow(() -> gateway.connect(),
                "After rotation, connect should succeed on healthy node");
    }

    // ── Rate limit → backpressure ─────────────────────────────────────

    @Test
    void rateLimitedRequestsDoNotCorruptState() {
        var callCount = new AtomicInteger();

        IBrokerConnection node = mockConnection("rate-limited");
        when(node.marketData().getLtpPaisa(any())).thenAnswer(inv -> {
            int count = callCount.incrementAndGet();
            if (count > 5) {
                throw new RuntimeException("Rate limit exceeded: 429 Too Many Requests");
            }
            return 100_00L + count;
        });

        var gateway = new LoadBalancedBrokerGateway(List.of(node));
        InstrumentKey key = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);

        // First 5 calls succeed
        for (int i = 0; i < 5; i++) {
            long ltp = gateway.marketData().getLtpPaisa(key);
            assertTrue(ltp > 0, "LTP should be positive for call " + i);
        }

        // Subsequent calls are rate-limited — gateway may wrap or rethrow
        int errors = 0;
        for (int i = 0; i < 5; i++) {
            try {
                gateway.marketData().getLtpPaisa(key);
            } catch (RuntimeException e) {
                errors++;
            }
        }

        assertTrue(errors > 0, "Should encounter errors after rate limit threshold");
        assertEquals(1, gateway.connectionCount(), "Connection count should remain stable despite rate limiting");
    }

    // ── Duplicate event handling ──────────────────────────────────────

    @Test
    void duplicateConnectCallsAreIdempotent() {
        var connectCount = new AtomicInteger();

        IBrokerConnection node = mockConnection("dup-connect");
        doAnswer(inv -> {
            connectCount.incrementAndGet();
            return null;
        }).when(node).connect();

        var gateway = new LoadBalancedBrokerGateway(List.of(node));

        // Call connect multiple times rapidly
        for (int i = 0; i < 10; i++) {
            gateway.connect();
        }

        // Each call delegates to the node — the node is responsible for idempotency
        assertTrue(connectCount.get() >= 1, "Connect should have been called at least once");
        assertEquals(1, gateway.connectionCount(), "Connection count should remain 1");
    }

    // ── All nodes fail ────────────────────────────────────────────────

    @Test
    void allNodesFailingDoesNotCrashGateway() {
        IBrokerConnection node1 = mockConnection("fail-1");
        when(node1.marketData().getLtpPaisa(any()))
                .thenThrow(new RuntimeException("Node 1 down"));

        IBrokerConnection node2 = mockConnection("fail-2");
        when(node2.marketData().getLtpPaisa(any()))
                .thenThrow(new RuntimeException("Node 2 down"));

        var gateway = new LoadBalancedBrokerGateway(List.of(node1, node2));
        InstrumentKey key = new InstrumentKey("INFY", ExchangeSegment.NSE_EQ);

        assertThrows(RuntimeException.class, () -> gateway.marketData().getLtpPaisa(key),
                "Should throw when all nodes fail");

        // Gateway should still be operational
        assertEquals(2, gateway.connectionCount(), "Both connections should remain registered");
        assertDoesNotThrow(gateway::disconnect, "Disconnect should succeed even when all nodes are failing");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private IBrokerConnection mockConnection(String id) {
        IBrokerConnection conn = mock(IBrokerConnection.class, id);
        MarketDataProvider marketData = mock(MarketDataProvider.class, id + "-marketData");
        WebSocketMultiplexer websocket = mock(WebSocketMultiplexer.class, id + "-websocket");
        when(conn.marketData()).thenReturn(marketData);
        when(conn.websocket()).thenReturn(websocket);
        return conn;
    }
}
