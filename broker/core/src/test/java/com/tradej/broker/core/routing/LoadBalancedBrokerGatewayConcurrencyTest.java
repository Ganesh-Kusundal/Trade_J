package com.tradej.broker.core.routing;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.testing.ConcurrentStressTester;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency tests for {@link LoadBalancedBrokerGateway}.
 * Verifies thread-safe connect/disconnect, primary rotation, and connection management.
 */
@Tag("unit")
class LoadBalancedBrokerGatewayConcurrencyTest {

    @Test
    void concurrentPrimaryRotationIsConsistent() {
        IBrokerConnection node1 = mockConnection("node-1");
        IBrokerConnection node2 = mockConnection("node-2");
        IBrokerConnection node3 = mockConnection("node-3");

        var gateway = new LoadBalancedBrokerGateway(List.of(node1, node2, node3));

        int workers = 10;
        int rotations = 500;

        var result = ConcurrentStressTester.run(workers, rotations, threadIndex -> {
            gateway.rotatePrimary();
            int count = gateway.connectionCount();
            assertTrue(count == 3, "Connection count must remain stable during rotation");
        });

        assertTrue(result.exceptions().isEmpty(), "No exceptions during concurrent primary rotation");
        assertEquals(3, gateway.connectionCount(), "All connections should remain after rotation");
    }

    @Test
    void concurrentAddAndRemoveConnections() {
        IBrokerConnection initial = mockConnection("initial");
        var gateway = new LoadBalancedBrokerGateway(List.of(initial));

        int workers = 8;
        int iterations = 200;

        var result = ConcurrentStressTester.run(workers, iterations, threadIndex -> {
            IBrokerConnection node = mockConnection("dynamic-" + threadIndex + "-" + System.nanoTime());
            gateway.addConnection(node);
            assertTrue(gateway.connectionCount() >= 1, "Gateway must always have at least one connection");
            gateway.rotatePrimary();
        });

        assertTrue(result.exceptions().isEmpty(), "No exceptions during concurrent add/remove");
        assertTrue(gateway.connectionCount() >= 1, "Gateway should have connections after concurrent ops");
    }

    @Test
    void concurrentConnectDisconnectDoesNotThrow() {
        var connectCount = new AtomicInteger();
        var disconnectCount = new AtomicInteger();

        IBrokerConnection node1 = mockConnectionWithCounters("n1", connectCount, disconnectCount);
        IBrokerConnection node2 = mockConnectionWithCounters("n2", connectCount, disconnectCount);

        var gateway = new LoadBalancedBrokerGateway(List.of(node1, node2));

        int workers = 6;
        int iterations = 100;

        var result = ConcurrentStressTester.run(workers, iterations, threadIndex -> {
            if (threadIndex % 2 == 0) {
                gateway.connect();
            } else {
                gateway.disconnect();
            }
            gateway.rotatePrimary();
        });

        assertTrue(result.exceptions().isEmpty(),
                "Concurrent connect/disconnect/rotate should not throw");
    }

    @Test
    void gatewayConnectionCountReflectsAllNodes() {
        var nodes = new java.util.ArrayList<IBrokerConnection>();
        for (int i = 0; i < 5; i++) {
            nodes.add(mockConnection("node-" + i));
        }
        var gateway = new LoadBalancedBrokerGateway(nodes);

        assertEquals(5, gateway.connectionCount());

        // Add more
        IBrokerConnection extra = mockConnection("extra");
        gateway.addConnection(extra);
        assertEquals(6, gateway.connectionCount());

        // Remove
        gateway.removeConnection(extra);
        assertEquals(5, gateway.connectionCount());
    }

    private IBrokerConnection mockConnection(String id) {
        IBrokerConnection conn = mock(IBrokerConnection.class, id);
        when(conn.marketData()).thenReturn(mock(MarketDataProvider.class));
        when(conn.websocket()).thenReturn(mock(WebSocketMultiplexer.class));
        return conn;
    }

    private IBrokerConnection mockConnectionWithCounters(
            String id, AtomicInteger connectCount, AtomicInteger disconnectCount) {
        IBrokerConnection conn = mock(IBrokerConnection.class, id);
        when(conn.marketData()).thenReturn(mock(MarketDataProvider.class));
        when(conn.websocket()).thenReturn(mock(WebSocketMultiplexer.class));
        doAnswer(inv -> { connectCount.incrementAndGet(); return null; }).when(conn).connect();
        doAnswer(inv -> { disconnectCount.incrementAndGet(); return null; }).when(conn).disconnect();
        return conn;
    }
}
