package com.tradej.app.e2e;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.HistoricalDataCapabilities;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end failover orchestrator test for the Dhan → ICICI → Upstox multi-broker
 * gateway. Closes audit Gap #20 ("no Dhan-ICICI failover orchestrator e2e test").
 *
 * <p>Boots a {@link LoadBalancedBrokerGateway} with 3 mocked broker connections
 * (Dhan as primary, ICICI as secondary, Upstox as tertiary) and verifies:
 * <ul>
 *   <li>Order failover from primary → secondary on primary failure</li>
 *   <li>Order recovery back to primary when it recovers</li>
 *   <li>Degraded state when all brokers fail</li>
 *   <li>Market data failover across broker nodes</li>
 *   <li>WebSocket fan-out preserves connectivity across nodes</li>
 * </ul>
 *
 * <p>Uses full mocks for the IBrokerConnection nodes (not @SpyBean) because the
 * real broker adapters require live credentials and network. The test exercises the
 * real {@link LoadBalancedBrokerGateway} failover logic — the mocks only stub
 * the I/O boundary (market data, orders, websocket).
 */
@Tag("e2e")
class BrokerGatewayFailoverE2ETest {

    private LoadBalancedBrokerGateway gateway;

    private IBrokerConnection dhanConnection;
    private IBrokerConnection iciciConnection;
    private IBrokerConnection upstoxConnection;

    private MarketDataProvider dhanMarketData;
    private MarketDataProvider iciciMarketData;
    private MarketDataProvider upstoxMarketData;

    private OrderCommand dhanOrders;
    private OrderCommand iciciOrders;
    private OrderCommand upstoxOrders;

    private WebSocketMultiplexer dhanWebSocket;
    private WebSocketMultiplexer iciciWebSocket;
    private WebSocketMultiplexer upstoxWebSocket;

    private OptionsProvider dhanOptions;

    @BeforeEach
    void setUp() {
        dhanConnection = mockBrokerConnection(BrokerSource.DHAN);
        iciciConnection = mockBrokerConnection(BrokerSource.ICICI);
        upstoxConnection = mockBrokerConnection(BrokerSource.UPSTOX);

        dhanMarketData = dhanConnection.marketData();
        iciciMarketData = iciciConnection.marketData();
        upstoxMarketData = upstoxConnection.marketData();

        dhanOrders = dhanConnection.orders();
        iciciOrders = iciciConnection.orders();
        upstoxOrders = upstoxConnection.orders();

        dhanWebSocket = dhanConnection.websocket();
        iciciWebSocket = iciciConnection.websocket();
        upstoxWebSocket = upstoxConnection.websocket();

        dhanOptions = dhanConnection.options();

        gateway = new LoadBalancedBrokerGateway(
                List.of(dhanConnection, iciciConnection, upstoxConnection));
    }

    // ── 1. Order failover: Dhan (primary) → ICICI (secondary) ──────────────

    @Test
    void failover_switchesToSecondaryBroker() {
        OrderRequest request = orderRequest("RELIANCE");
        Order dhanOrder = mock(Order.class);
        Order iciciOrder = mock(Order.class);

        when(dhanOrders.placeOrder(request)).thenThrow(new RuntimeException("Dhan connection refused"));
        when(iciciOrders.placeOrder(request)).thenReturn(iciciOrder);

        Order result = gateway.orders().placeOrder(request);

        assertThat(result).isSameAs(iciciOrder);
        verify(dhanOrders).placeOrder(request);
        verify(iciciOrders).placeOrder(request);
    }

    // ── 2. Order recovery: full failover cycle wraps back to Dhan ──────────

    @Test
    void failover_recoversToPrimaryBroker() {
        OrderRequest request = orderRequest("RELIANCE");
        Order dhanOrder = mock(Order.class);
        Order iciciOrder = mock(Order.class);
        Order upstoxOrder = mock(Order.class);

        org.mockito.Mockito.doThrow(new RuntimeException("Dhan down"))
                .when(dhanOrders).placeOrder(request);
        org.mockito.Mockito.doReturn(iciciOrder)
                .when(iciciOrders).placeOrder(request);
        org.mockito.Mockito.doReturn(upstoxOrder)
                .when(upstoxOrders).placeOrder(request);

        Order firstResult = gateway.orders().placeOrder(request);
        assertThat(firstResult).isSameAs(iciciOrder);

        org.mockito.Mockito.doThrow(new RuntimeException("ICICI down"))
                .when(iciciOrders).placeOrder(request);

        Order secondResult = gateway.orders().placeOrder(request);
        assertThat(secondResult).isSameAs(upstoxOrder);

        org.mockito.Mockito.doThrow(new RuntimeException("Upstox down"))
                .when(upstoxOrders).placeOrder(request);
        org.mockito.Mockito.doReturn(dhanOrder)
                .when(dhanOrders).placeOrder(request);

        Order thirdResult = gateway.orders().placeOrder(request);
        assertThat(thirdResult).isSameAs(dhanOrder);
    }

    // ── 3. All brokers down → degraded state ───────────────────────────────

    @Test
    void failover_allBrokersDown_throwsDegradedState() {
        OrderRequest request = orderRequest("RELIANCE");

        when(dhanOrders.placeOrder(request)).thenThrow(new RuntimeException("Dhan down"));
        when(iciciOrders.placeOrder(request)).thenThrow(new RuntimeException("ICICI down"));
        when(upstoxOrders.placeOrder(request)).thenThrow(new RuntimeException("Upstox down"));

        assertThatThrownBy(() -> gateway.orders().placeOrder(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All broker order nodes failed");
    }

    // ── 4. Market data failover: Dhan → ICICI ──────────────────────────────

    @Test
    void failover_marketDataSwitchesToSecondaryBroker() {
        InstrumentKey key = InstrumentKey.of("RELIANCE", ExchangeSegment.NSE_EQ);

        when(dhanMarketData.getLtpPaisa(key)).thenThrow(new RuntimeException("Dhan market data down"));
        when(iciciMarketData.getLtpPaisa(key)).thenReturn(245000L);

        long ltp = gateway.marketData().getLtpPaisa(key);

        assertThat(ltp).isEqualTo(245000L);
    }

    // ── 5. Market data: all brokers down → IllegalStateException ────────────

    @Test
    void failover_marketDataAllBrokersDown_throws() {
        InstrumentKey key = InstrumentKey.of("RELIANCE", ExchangeSegment.NSE_EQ);

        when(dhanMarketData.getLtpPaisa(key)).thenThrow(new RuntimeException("Dhan down"));
        when(iciciMarketData.getLtpPaisa(key)).thenThrow(new RuntimeException("ICICI down"));
        when(upstoxMarketData.getLtpPaisa(key)).thenThrow(new RuntimeException("Upstox down"));

        assertThatThrownBy(() -> gateway.marketData().getLtpPaisa(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All broker market-data nodes failed");
    }

    // ── 6. WebSocket: fans out to all nodes ────────────────────────────────

    @Test
    void failover_webSocketSubscribesToAllNodes() {
        MarketSubscriptionRequest sub = new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ);
        List<MarketSubscriptionRequest> subs = List.of(sub);

        gateway.websocket().subscribe(subs, FeedMode.QUOTE);

        verify(dhanWebSocket).subscribe(subs, FeedMode.QUOTE);
        verify(iciciWebSocket).subscribe(subs, FeedMode.QUOTE);
        verify(upstoxWebSocket).subscribe(subs, FeedMode.QUOTE);
    }

    @Test
    void failover_webSocketIsConnectedIfAnyNodeConnected() {
        when(dhanWebSocket.isConnected()).thenReturn(false);
        when(iciciWebSocket.isConnected()).thenReturn(true);
        when(upstoxWebSocket.isConnected()).thenReturn(false);

        assertThat(gateway.websocket().isConnected()).isTrue();
    }

    @Test
    void failover_webSocketIsDisconnectedIfNoNodeConnected() {
        when(dhanWebSocket.isConnected()).thenReturn(false);
        when(iciciWebSocket.isConnected()).thenReturn(false);
        when(upstoxWebSocket.isConnected()).thenReturn(false);

        assertThat(gateway.websocket().isConnected()).isFalse();
    }

    // ── 7. Connection management ───────────────────────────────────────────

    @Test
    void failover_connectionCountReflectsAllNodes() {
        assertThat(gateway.connectionCount()).isEqualTo(3);
    }

    @Test
    void failover_addConnectionIncreasesNodeCount() {
        IBrokerConnection tertiary = mock(IBrokerConnection.class);
        when(tertiary.websocket()).thenReturn(mock(WebSocketMultiplexer.class));
        when(tertiary.getCapability(OptionsProvider.class)).thenReturn(Optional.empty());
        when(tertiary.getCapability(FuturesProvider.class)).thenReturn(Optional.empty());

        gateway.addConnection(tertiary);

        assertThat(gateway.connectionCount()).isEqualTo(4);
    }

    // ── 8. Kill switch propagates to all brokers ───────────────────────────

    @Test
    void failover_killSwitchSetsOnAllBrokers() {
        gateway.orders().setKillSwitch(true);

        verify(dhanOrders).setKillSwitch(true);
        verify(iciciOrders).setKillSwitch(true);
        verify(upstoxOrders).setKillSwitch(true);
    }

    // ── 9. Cancel order failover ───────────────────────────────────────────

    @Test
    void failover_cancelOrderSwitchesToSecondaryBroker() {
        when(dhanOrders.cancelOrder("ORD-999")).thenThrow(new RuntimeException("Dhan down"));
        when(iciciOrders.cancelOrder("ORD-999")).thenReturn(true);

        boolean cancelled = gateway.orders().cancelOrder("ORD-999");

        assertThat(cancelled).isTrue();
    }

    // ── 10. Options capability resolves from first broker that supports it ──

    @Test
    void failover_optionsCapabilityResolvesFromPrimaryWhenSupported() {
        OptionsProvider options = gateway.options();
        assertThat(options).isSameAs(dhanOptions);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static IBrokerConnection mockBrokerConnection(BrokerSource source) {
        IBrokerConnection conn = mock(IBrokerConnection.class);
        lenient().when(conn.source()).thenReturn(source);

        MarketDataProvider md = mock(MarketDataProvider.class);
        lenient().when(conn.marketData()).thenReturn(md);

        OrderCommand orders = mock(OrderCommand.class);
        lenient().when(conn.orders()).thenReturn(orders);

        WebSocketMultiplexer ws = mock(WebSocketMultiplexer.class);
        lenient().when(ws.subscriptions()).thenReturn(Map.of());
        lenient().when(conn.websocket()).thenReturn(ws);

        OptionsProvider options = mock(OptionsProvider.class);
        lenient().when(conn.getCapability(OptionsProvider.class)).thenReturn(Optional.of(options));
        lenient().when(conn.options()).thenReturn(options);

        FuturesProvider futures = mock(FuturesProvider.class);
        lenient().when(conn.getCapability(FuturesProvider.class)).thenReturn(Optional.of(futures));

        return conn;
    }

    private static OrderRequest orderRequest(String symbol) {
        return new OrderRequest(
                symbol, ExchangeSegment.NSE_EQ, Side.BUY, 100L,
                OrderType.LIMIT, 245000L, 0L, ProductType.INTRADAY,
                Validity.DAY, "failover-e2e"
        );
    }
}
