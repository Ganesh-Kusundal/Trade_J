package com.tradej.app.e2e;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Tag("runtime-e2e")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrokerFailoverEndToEndTest {

    private LoadBalancedBrokerGateway gateway;

    @Mock
    private IBrokerConnection primaryConnection;

    @Mock
    private IBrokerConnection secondaryConnection;

    @Mock
    private MarketDataProvider primaryMarketData;

    @Mock
    private MarketDataProvider secondaryMarketData;

    @Mock
    private OrderCommand primaryOrderCommand;

    @Mock
    private OrderCommand secondaryOrderCommand;

    @Mock
    private WebSocketMultiplexer primaryWebSocket;

    @Mock
    private WebSocketMultiplexer secondaryWebSocket;

    @Mock
    private OrderQuery primaryOrderQuery;

    @Mock
    private PortfolioProvider primaryPortfolio;

    @Mock
    private OptionsProvider primaryOptions;

    @Mock
    private FuturesProvider primaryFutures;

    @BeforeEach
    void setUp() {
        when(primaryConnection.marketData()).thenReturn(primaryMarketData);
        when(primaryConnection.orders()).thenReturn(primaryOrderCommand);
        when(primaryConnection.websocket()).thenReturn(primaryWebSocket);
        when(primaryConnection.orderQuery()).thenReturn(primaryOrderQuery);
        when(primaryConnection.portfolio()).thenReturn(primaryPortfolio);
        when(primaryConnection.getCapability(OptionsProvider.class)).thenReturn(Optional.of(primaryOptions));
        when(primaryConnection.getCapability(FuturesProvider.class)).thenReturn(Optional.of(primaryFutures));
        when(primaryConnection.options()).thenReturn(primaryOptions);

        when(secondaryConnection.marketData()).thenReturn(secondaryMarketData);
        when(secondaryConnection.orders()).thenReturn(secondaryOrderCommand);
        when(secondaryConnection.websocket()).thenReturn(secondaryWebSocket);
        when(secondaryConnection.orderQuery()).thenReturn(primaryOrderQuery);
        when(secondaryConnection.portfolio()).thenReturn(primaryPortfolio);
        when(secondaryConnection.getCapability(OptionsProvider.class)).thenReturn(Optional.empty());
        when(secondaryConnection.getCapability(FuturesProvider.class)).thenReturn(Optional.empty());

        gateway = new LoadBalancedBrokerGateway(List.of(primaryConnection, secondaryConnection));
    }

    @Test
    void gatewayConnectsToAllBrokerNodes() {
        gateway.connect();

        org.mockito.Mockito.verify(primaryConnection).connect();
        org.mockito.Mockito.verify(secondaryConnection).connect();
    }

    @Test
    void gatewayDisconnectsFromAllBrokerNodes() {
        gateway.disconnect();

        org.mockito.Mockito.verify(primaryConnection).disconnect();
        org.mockito.Mockito.verify(secondaryConnection).disconnect();
    }

    @Test
    void marketDataFailoverFromPrimaryToSecondary() {
        InstrumentKey key = InstrumentKey.of("RELIANCE", ExchangeSegment.NSE_EQ);
        when(primaryMarketData.getLtpPaisa(key)).thenThrow(new RuntimeException("Primary down"));
        when(secondaryMarketData.getLtpPaisa(key)).thenReturn(245000L);

        long ltp = gateway.marketData().getLtpPaisa(key);

        assertThat(ltp).isEqualTo(245000L);
    }

    @Test
    void marketDataThrowsWhenAllBrokersFail() {
        InstrumentKey key = InstrumentKey.of("RELIANCE", ExchangeSegment.NSE_EQ);
        when(primaryMarketData.getLtpPaisa(key)).thenThrow(new RuntimeException("Primary down"));
        when(secondaryMarketData.getLtpPaisa(key)).thenThrow(new RuntimeException("Secondary down"));

        assertThatThrownBy(() -> gateway.marketData().getLtpPaisa(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All broker market-data nodes failed");
    }

    @Test
    void orderPlacementFailoverFromPrimaryToSecondary() {
        OrderRequest request = new OrderRequest(
                "RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY, 100L,
                OrderType.LIMIT, 245000L, 0L, ProductType.INTRADAY,
                Validity.DAY, "test-corr"
        );
        Order expectedOrder = new Order(
                "ORD-FAIL", "test-corr", "RELIANCE", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.OPEN,
                100L, 0L, 245000L, 0L, System.currentTimeMillis(), null
        );

        when(primaryOrderCommand.placeOrder(request)).thenThrow(new RuntimeException("Primary down"));
        when(secondaryOrderCommand.placeOrder(request)).thenReturn(expectedOrder);

        Order placed = gateway.orders().placeOrder(request);

        assertThat(placed.orderId()).isEqualTo("ORD-FAIL");
        org.mockito.Mockito.verify(primaryOrderCommand).placeOrder(request);
        org.mockito.Mockito.verify(secondaryOrderCommand).placeOrder(request);
    }

    @Test
    void orderPlacementFailsWhenAllBrokersFail() {
        OrderRequest request = new OrderRequest(
                "RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY, 100L,
                OrderType.LIMIT, 245000L, 0L, ProductType.INTRADAY,
                Validity.DAY, "test-corr"
        );

        when(primaryOrderCommand.placeOrder(request)).thenThrow(new RuntimeException("Primary down"));
        when(secondaryOrderCommand.placeOrder(request)).thenThrow(new RuntimeException("Secondary down"));

        assertThatThrownBy(() -> gateway.orders().placeOrder(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All broker order nodes failed");
    }

    @Test
    void cancelOrderFailoverFromPrimaryToSecondary() {
        when(primaryOrderCommand.cancelOrder("ORD-123")).thenThrow(new RuntimeException("Primary down"));
        when(secondaryOrderCommand.cancelOrder("ORD-123")).thenReturn(true);

        boolean cancelled = gateway.orders().cancelOrder("ORD-123");

        assertThat(cancelled).isTrue();
    }

    @Test
    void websocketFanoutSubscribesToAllNodes() {
        MarketSubscriptionRequest sub = new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ);
        List<MarketSubscriptionRequest> subs = List.of(sub);

        gateway.websocket().subscribe(subs, FeedMode.QUOTE);

        org.mockito.Mockito.verify(primaryWebSocket).subscribe(subs, FeedMode.QUOTE);
        org.mockito.Mockito.verify(secondaryWebSocket).subscribe(subs, FeedMode.QUOTE);
    }

    @Test
    void websocketUnsubscribeFansOutToAllNodes() {
        MarketSubscriptionRequest sub = new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ);
        List<MarketSubscriptionRequest> subs = List.of(sub);

        gateway.websocket().unsubscribe(subs);

        org.mockito.Mockito.verify(primaryWebSocket).unsubscribe(subs);
        org.mockito.Mockito.verify(secondaryWebSocket).unsubscribe(subs);
    }

    @Test
    void websocketConnectFansOutToAllNodes() {
        gateway.websocket().connect();

        org.mockito.Mockito.verify(primaryWebSocket).connect();
        org.mockito.Mockito.verify(secondaryWebSocket).connect();
    }

    @Test
    void websocketDisconnectFansOutToAllNodes() {
        gateway.websocket().disconnect();

        org.mockito.Mockito.verify(primaryWebSocket).disconnect();
        org.mockito.Mockito.verify(secondaryWebSocket).disconnect();
    }

    @Test
    void websocketIsConnectedReturnsTrueIfAnyNodeConnected() {
        when(primaryWebSocket.isConnected()).thenReturn(false);
        when(secondaryWebSocket.isConnected()).thenReturn(true);

        assertThat(gateway.websocket().isConnected()).isTrue();
    }

    @Test
    void websocketIsConnectedReturnsFalseIfNoNodeConnected() {
        when(primaryWebSocket.isConnected()).thenReturn(false);
        when(secondaryWebSocket.isConnected()).thenReturn(false);

        assertThat(gateway.websocket().isConnected()).isFalse();
    }

    @Test
    void marketDataFailoverForGetQuote() {
        InstrumentKey key = InstrumentKey.of("INFY", ExchangeSegment.NSE_EQ);
        Quote expectedQuote = new Quote(
                null, 150000L, 149000L, 151000L, 148000L, 149500L,
                5000L, 2500L, 2500L, 10000L, System.currentTimeMillis()
        );

        when(primaryMarketData.getQuote(key)).thenThrow(new RuntimeException("Primary down"));
        when(secondaryMarketData.getQuote(key)).thenReturn(expectedQuote);

        Quote quote = gateway.marketData().getQuote(key);

        assertThat(quote.ltpPaisa()).isEqualTo(150000L);
    }

    @Test
    void connectionCountReflectsAllNodes() {
        assertThat(gateway.connectionCount()).isEqualTo(2);
    }

    @Test
    void addConnectionIncreasesNodeCount() {
        IBrokerConnection tertiary = org.mockito.Mockito.mock(IBrokerConnection.class);
        when(tertiary.websocket()).thenReturn(org.mockito.Mockito.mock(WebSocketMultiplexer.class));
        when(tertiary.getCapability(OptionsProvider.class)).thenReturn(Optional.empty());
        when(tertiary.getCapability(FuturesProvider.class)).thenReturn(Optional.empty());

        gateway.addConnection(tertiary);

        assertThat(gateway.connectionCount()).isEqualTo(3);
    }

    @Test
    void removeConnectionDecreasesNodeCount() {
        gateway.removeConnection(secondaryConnection);

        assertThat(gateway.connectionCount()).isEqualTo(1);
    }

    @Test
    void killSwitchSetsOnAllBrokers() {
        gateway.orders().setKillSwitch(true);

        org.mockito.Mockito.verify(primaryOrderCommand).setKillSwitch(true);
        org.mockito.Mockito.verify(secondaryOrderCommand).setKillSwitch(true);
    }

    @Test
    void websocketSubscriptionsMergesFromAllNodes() {
        MarketSubscriptionRequest sub1 = new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ);
        MarketSubscriptionRequest sub2 = new MarketSubscriptionRequest("INFY", ExchangeSegment.NSE_EQ);

        when(primaryWebSocket.subscriptions()).thenReturn(Map.of(sub1, FeedMode.QUOTE));
        when(secondaryWebSocket.subscriptions()).thenReturn(Map.of(sub2, FeedMode.FULL));

        Map<MarketSubscriptionRequest, FeedMode> merged = gateway.websocket().subscriptions();

        assertThat(merged).containsEntry(sub1, FeedMode.QUOTE);
        assertThat(merged).containsEntry(sub2, FeedMode.FULL);
    }

    @Test
    void optionsCapabilityResolvesFromPrimaryWhenSupported() {
        OptionsProvider optionsProvider = gateway.options();
        assertThat(optionsProvider).isSameAs(primaryOptions);
    }

    @Test
    void orderQueryDelegatesToPrimary() {
        when(primaryOrderQuery.getOrder("ORD-X")).thenReturn(null);

        Order result = gateway.orderQuery().getOrder("ORD-X");

        assertThat(result).isNull();
        org.mockito.Mockito.verify(primaryOrderQuery).getOrder("ORD-X");
    }
}
