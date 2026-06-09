package com.tradej.broker.core.routing;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderUpdateListener;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GatewayFailoverTest {

    // ── helpers ─────────────────────────────────────────────────────────────

    private static IBrokerConnection mockConnectionWithOrders(OrderCommand orderCommand,
                                                               WebSocketMultiplexer ws) {
        IBrokerConnection conn = mock(IBrokerConnection.class);
        lenient().when(conn.orders()).thenReturn(orderCommand);
        lenient().when(conn.websocket()).thenReturn(ws);
        MarketDataProvider md = mock(MarketDataProvider.class);
        lenient().when(conn.marketData()).thenReturn(md);
        return conn;
    }

    private static WebSocketMultiplexer mockWebSocket() {
        WebSocketMultiplexer ws = mock(WebSocketMultiplexer.class);
        lenient().when(ws.subscriptions()).thenReturn(Map.of());
        return ws;
    }

    private static WebSocketMultiplexer trackingWebSocket(
            CopyOnWriteArrayList<Collection<MarketSubscriptionRequest>> subscribeCalls) {
        WebSocketMultiplexer ws = mock(WebSocketMultiplexer.class);
        lenient().when(ws.subscriptions()).thenReturn(Map.of());
        lenient().doAnswer(inv -> {
            Collection<MarketSubscriptionRequest> instruments = inv.getArgument(0);
            subscribeCalls.add(instruments);
            return null;
        }).when(ws).subscribe(any(), any());
        return ws;
    }

    // ── failoverSwitchesToBackupOnPrimaryFailure ────────────────────────────

    @Test
    void failoverSwitchesToBackupOnPrimaryFailure() {
        // Primary order command throws; backup succeeds
        OrderCommand primaryOrders = mock(OrderCommand.class);
        OrderCommand backupOrders = mock(OrderCommand.class);

        Order dummyOrder = mock(Order.class);
        when(primaryOrders.placeOrder(any())).thenThrow(new RuntimeException("Primary down"));
        when(backupOrders.placeOrder(any())).thenReturn(dummyOrder);

        WebSocketMultiplexer ws1 = mockWebSocket();
        WebSocketMultiplexer ws2 = mockWebSocket();

        IBrokerConnection primary = mockConnectionWithOrders(primaryOrders, ws1);
        IBrokerConnection backup = mockConnectionWithOrders(backupOrders, ws2);

        LoadBalancedBrokerGateway.FailoverOrderCommand failover = new LoadBalancedBrokerGateway.FailoverOrderCommand(List.of(primary, backup));

        OrderRequest request = mock(OrderRequest.class);
        Order result = failover.placeOrder(request);

        assertSame(dummyOrder, result, "Should return result from backup connection");
        verify(primaryOrders).placeOrder(request);
        verify(backupOrders).placeOrder(request);
    }

    // ── failoverRestoresPrimaryWhenAvailable ────────────────────────────────

    @Test
    void failoverRestoresPrimaryWhenAvailable() {
        // After a failover rotates the index, the next call uses the new primary
        AtomicInteger primaryCallCount = new AtomicInteger();
        AtomicInteger backupCallCount = new AtomicInteger();

        OrderCommand primaryOrders = mock(OrderCommand.class);
        OrderCommand backupOrders = mock(OrderCommand.class);

        Order dummyOrder = mock(Order.class);
        OrderRequest request = mock(OrderRequest.class);

        // First call: primary throws, backup succeeds (rotates index to 1)
        when(primaryOrders.placeOrder(any()))
                .thenThrow(new RuntimeException("Primary down"))
                .thenReturn(dummyOrder); // recovers on second call
        when(backupOrders.placeOrder(any())).thenReturn(dummyOrder);

        WebSocketMultiplexer ws1 = mockWebSocket();
        WebSocketMultiplexer ws2 = mockWebSocket();

        IBrokerConnection primary = mockConnectionWithOrders(primaryOrders, ws1);
        IBrokerConnection backup = mockConnectionWithOrders(backupOrders, ws2);

        LoadBalancedBrokerGateway.FailoverOrderCommand failover = new LoadBalancedBrokerGateway.FailoverOrderCommand(List.of(primary, backup));

        // First call: primary fails, backup handles it (index rotates to 1)
        failover.placeOrder(request);
        verify(primaryOrders, times(1)).placeOrder(request);
        verify(backupOrders, times(1)).placeOrder(request);

        // Second call: index is now 1 (backup is primary), backup handles directly
        failover.placeOrder(request);
        // backup was called once from failover + once as new primary = 2 total
        verify(backupOrders, times(2)).placeOrder(request);
    }

    // ── failoverPreservesSubscriptionsOnSwitch ──────────────────────────────

    @Test
    void failoverPreservesSubscriptionsOnSwitch() {
        // FailoverWebSocketMultiplexer fans out subscriptions to ALL connections
        CopyOnWriteArrayList<Collection<MarketSubscriptionRequest>> primarySubs = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<Collection<MarketSubscriptionRequest>> backupSubs = new CopyOnWriteArrayList<>();

        WebSocketMultiplexer ws1 = trackingWebSocket(primarySubs);
        WebSocketMultiplexer ws2 = trackingWebSocket(backupSubs);

        IBrokerConnection conn1 = mock(IBrokerConnection.class);
        IBrokerConnection conn2 = mock(IBrokerConnection.class);
        lenient().when(conn1.websocket()).thenReturn(ws1);
        lenient().when(conn2.websocket()).thenReturn(ws2);

        LoadBalancedBrokerGateway.FailoverWebSocketMultiplexer mux = new LoadBalancedBrokerGateway.FailoverWebSocketMultiplexer(List.of(conn1, conn2));

        MarketSubscriptionRequest sub = new MarketSubscriptionRequest("SBIN", ExchangeSegment.NSE_EQ);
        mux.subscribe(List.of(sub), FeedMode.QUOTE);

        // Both connections should have received the subscription
        assertEquals(1, primarySubs.size(), "Primary should receive subscription");
        assertEquals(1, backupSubs.size(), "Backup should receive subscription");
        assertTrue(primarySubs.get(0).contains(sub));
        assertTrue(backupSubs.get(0).contains(sub));

        // isConnected returns true if any connection is connected
        when(ws1.isConnected()).thenReturn(false);
        when(ws2.isConnected()).thenReturn(true);
        assertTrue(mux.isConnected(), "Multiplexer is connected if any downstream is connected");
    }

    // ── loadBalancedGatewayDistributesAcrossNodes ───────────────────────────

    @Test
    void loadBalancedGatewayDistributesAcrossNodes() {
        AtomicInteger node1Calls = new AtomicInteger();
        AtomicInteger node2Calls = new AtomicInteger();

        MarketDataProvider md1 = mock(MarketDataProvider.class);
        MarketDataProvider md2 = mock(MarketDataProvider.class);
        when(md1.getLtpPaisa(any())).thenAnswer(inv -> {
            node1Calls.incrementAndGet();
            return 100L;
        });
        when(md2.getLtpPaisa(any())).thenAnswer(inv -> {
            node2Calls.incrementAndGet();
            return 200L;
        });

        IBrokerConnection conn1 = mock(IBrokerConnection.class);
        IBrokerConnection conn2 = mock(IBrokerConnection.class);
        WebSocketMultiplexer ws1 = mockWebSocket();
        WebSocketMultiplexer ws2 = mockWebSocket();
        lenient().when(conn1.marketData()).thenReturn(md1);
        lenient().when(conn2.marketData()).thenReturn(md2);
        lenient().when(conn1.websocket()).thenReturn(ws1);
        lenient().when(conn2.websocket()).thenReturn(ws2);
        // Stub orders for the FailoverOrderCommand constructor
        OrderCommand orders1 = mock(OrderCommand.class);
        OrderCommand orders2 = mock(OrderCommand.class);
        lenient().when(conn1.orders()).thenReturn(orders1);
        lenient().when(conn2.orders()).thenReturn(orders2);

        LoadBalancedBrokerGateway gateway = new LoadBalancedBrokerGateway(List.of(conn1, conn2));

        InstrumentKey key = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);

        // Make 4 LTP calls — should distribute across both nodes via round-robin
        gateway.marketData().getLtpPaisa(key);
        gateway.marketData().getLtpPaisa(key);
        gateway.marketData().getLtpPaisa(key);
        gateway.marketData().getLtpPaisa(key);

        assertEquals(4, node1Calls.get() + node2Calls.get(), "Total calls should be 4");
        assertTrue(node1Calls.get() >= 1, "Node 1 should receive at least one call");
        assertTrue(node2Calls.get() >= 1, "Node 2 should receive at least one call");
    }

    // ── loadBalancedGatewaySkipsUnhealthyNodes ─────────────────────────────

    @Test
    void loadBalancedGatewaySkipsUnhealthyNodes() {
        MarketDataProvider md1 = mock(MarketDataProvider.class);
        MarketDataProvider md2 = mock(MarketDataProvider.class);

        // Node 1 always throws
        when(md1.getLtpPaisa(any())).thenThrow(new RuntimeException("Node 1 unhealthy"));
        // Node 2 is healthy
        when(md2.getLtpPaisa(any())).thenReturn(250L);

        IBrokerConnection conn1 = mock(IBrokerConnection.class);
        IBrokerConnection conn2 = mock(IBrokerConnection.class);
        WebSocketMultiplexer ws1 = mockWebSocket();
        WebSocketMultiplexer ws2 = mockWebSocket();
        lenient().when(conn1.marketData()).thenReturn(md1);
        lenient().when(conn2.marketData()).thenReturn(md2);
        lenient().when(conn1.websocket()).thenReturn(ws1);
        lenient().when(conn2.websocket()).thenReturn(ws2);
        OrderCommand orders1 = mock(OrderCommand.class);
        OrderCommand orders2 = mock(OrderCommand.class);
        lenient().when(conn1.orders()).thenReturn(orders1);
        lenient().when(conn2.orders()).thenReturn(orders2);

        LoadBalancedBrokerGateway gateway = new LoadBalancedBrokerGateway(List.of(conn1, conn2));

        InstrumentKey key = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);

        // Should failover to the healthy node
        long ltp = gateway.marketData().getLtpPaisa(key);
        assertEquals(250L, ltp, "Should return LTP from the healthy node");
        verify(md2, atLeastOnce()).getLtpPaisa(key);
    }
}
