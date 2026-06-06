package com.tradej.brokergateway.simulation;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BacktestBrokerConnectionTest {

    private static final long START_TIME_MS = 1_700_000_000_000L;

    private BrokerClock clock;
    private BacktestBrokerConnection connection;

    @BeforeEach
    void setUp() {
        clock = new BrokerClock(START_TIME_MS);
        connection = new BacktestBrokerConnection(clock);
    }

    // ── getCapability() ───────────────────────────────────────────────────

    @Test
    void getCapabilityReturnsMarketDataProvider() {
        Optional<MarketDataProvider> cap = connection.getCapability(MarketDataProvider.class);
        assertTrue(cap.isPresent());
        assertInstanceOf(SimulatedMarketDataProvider.class, cap.get());
    }

    @Test
    void getCapabilityReturnsPortfolioProvider() {
        Optional<PortfolioProvider> cap = connection.getCapability(PortfolioProvider.class);
        assertTrue(cap.isPresent());
        assertInstanceOf(SimulationPortfolioProvider.class, cap.get());
    }

    @Test
    void getCapabilityReturnsOrderCommand() {
        Optional<OrderCommand> cap = connection.getCapability(OrderCommand.class);
        assertTrue(cap.isPresent());
        assertNotNull(cap.get());
    }

    @Test
    void getCapabilityReturnsOrderQuery() {
        Optional<OrderQuery> cap = connection.getCapability(OrderQuery.class);
        assertTrue(cap.isPresent());
        assertNotNull(cap.get());
    }

    @Test
    void getCapabilityReturnsInstrumentResolver() {
        Optional<InstrumentResolver> cap = connection.getCapability(InstrumentResolver.class);
        assertTrue(cap.isPresent());
        assertTrue(cap.get().isLoaded());
    }

    @Test
    void getCapabilityReturnsWebSocketMultiplexer() {
        Optional<WebSocketMultiplexer> cap = connection.getCapability(WebSocketMultiplexer.class);
        assertTrue(cap.isPresent());
        assertInstanceOf(SimulatedWebSocketMultiplexer.class, cap.get());
    }

    @Test
    void getCapabilityReturnsEmptyForUnsupportedType() {
        Optional<String> cap = connection.getCapability(String.class);
        assertTrue(cap.isEmpty());
    }

    @Test
    void getCapabilityReturnsEmptyForRunnable() {
        Optional<Runnable> cap = connection.getCapability(Runnable.class);
        assertTrue(cap.isEmpty());
    }

    // ── placeOrder() ──────────────────────────────────────────────────────

    @Test
    void placeOrderCreatesOrderWithVirtualClockTimestamp() {
        clock.set(1_700_500_000_000L);

        OrderRequest request = new OrderRequest(
                "RELIANCE", ExchangeSegment.NSE_EQ,
                Side.BUY, 10, OrderType.LIMIT, 250_000L, 0L,
                ProductType.INTRADAY, Validity.DAY, "corr-1");

        OrderCommand cmd = connection.getCapability(OrderCommand.class).orElseThrow();
        Order order = cmd.placeOrder(request);

        assertNotNull(order);
        assertEquals("RELIANCE", order.symbol());
        assertEquals(Side.BUY, order.side());
        assertEquals(10, order.filledQuantity());
        assertEquals(OrderStatus.TRADED, order.status());
        assertEquals(1_700_500_000_000L, order.exchangeTimeMs());
    }

    @Test
    void placeOrderUsesSpecifiedPriceForLimitOrder() {
        OrderRequest request = new OrderRequest(
                "TCS", ExchangeSegment.NSE_EQ,
                Side.BUY, 5, OrderType.LIMIT, 380_000L, 0L,
                ProductType.CNC, Validity.DAY, null);

        OrderCommand cmd = connection.getCapability(OrderCommand.class).orElseThrow();
        Order order = cmd.placeOrder(request);

        assertEquals(380_000L, order.pricePaisa());
    }

    @Test
    void placeOrderGeneratesUniqueOrderIds() {
        OrderRequest request = new OrderRequest(
                "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, 10, OrderType.MARKET, 0L, 0L,
                ProductType.INTRADAY, Validity.DAY, null);

        OrderCommand cmd = connection.getCapability(OrderCommand.class).orElseThrow();
        Order order1 = cmd.placeOrder(request);
        Order order2 = cmd.placeOrder(request);

        assertNotEquals(order1.orderId(), order2.orderId());
        assertTrue(order1.orderId().startsWith("BT-"));
        assertTrue(order2.orderId().startsWith("BT-"));
    }

    // ── placeOrder() records in trade history ─────────────────────────────

    @Test
    void placeOrderRecordsInTradeHistory() {
        clock.set(1_700_100_000_000L);

        OrderRequest request = new OrderRequest(
                "INFY", ExchangeSegment.NSE_EQ,
                Side.SELL, 20, OrderType.LIMIT, 155_000L, 0L,
                ProductType.INTRADAY, Validity.DAY, null);

        OrderCommand cmd = connection.getCapability(OrderCommand.class).orElseThrow();
        Order order = cmd.placeOrder(request);

        var history = connection.getTradeHistory();
        assertEquals(1, history.size());

        var record = history.get(0);
        assertEquals(order.orderId(), record.orderId());
        assertEquals("INFY", record.symbol());
        assertEquals("SELL", record.side());
        assertEquals(20, record.quantity());
        assertEquals(155_000L, record.pricePaisa());
        assertEquals(1_700_100_000_000L, record.timestampMs());
    }

    // ── getTradeHistory() ─────────────────────────────────────────────────

    @Test
    void getTradeHistoryReturnsAllPlacedTrades() {
        OrderCommand cmd = connection.getCapability(OrderCommand.class).orElseThrow();

        cmd.placeOrder(new OrderRequest("RELIANCE", ExchangeSegment.NSE_EQ,
                Side.BUY, 10, OrderType.LIMIT, 250_000L, 0L,
                ProductType.INTRADAY, Validity.DAY, null));
        cmd.placeOrder(new OrderRequest("TCS", ExchangeSegment.NSE_EQ,
                Side.SELL, 5, OrderType.LIMIT, 380_000L, 0L,
                ProductType.CNC, Validity.DAY, null));
        cmd.placeOrder(new OrderRequest("SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, 100, OrderType.MARKET, 0L, 0L,
                ProductType.INTRADAY, Validity.DAY, null));

        var history = connection.getTradeHistory();
        assertEquals(3, history.size());
        assertEquals("RELIANCE", history.get(0).symbol());
        assertEquals("TCS", history.get(1).symbol());
        assertEquals("SBIN", history.get(2).symbol());
    }

    @Test
    void getTradeHistoryIsEmptyInitially() {
        assertTrue(connection.getTradeHistory().isEmpty());
    }

    @Test
    void getTradeHistoryReturnsImmutableCopy() {
        OrderCommand cmd = connection.getCapability(OrderCommand.class).orElseThrow();
        cmd.placeOrder(new OrderRequest("RELIANCE", ExchangeSegment.NSE_EQ,
                Side.BUY, 10, OrderType.LIMIT, 250_000L, 0L,
                ProductType.INTRADAY, Validity.DAY, null));

        var history = connection.getTradeHistory();
        assertThrows(UnsupportedOperationException.class, () ->
                history.add(new BacktestBrokerConnection.TradeRecord(
                        "fake", "FAKE", "BUY", 1, 100L, 0L)));
    }

    // ── connect() and disconnect() ────────────────────────────────────────

    @Test
    void connectIsNoOp() {
        assertDoesNotThrow(() -> connection.connect());
    }

    @Test
    void disconnectIsNoOp() {
        assertDoesNotThrow(() -> connection.disconnect());
    }

    @Test
    void connectAndDisconnectCanBeCalledMultipleTimes() {
        assertDoesNotThrow(() -> {
            connection.connect();
            connection.disconnect();
            connection.connect();
            connection.disconnect();
        });
    }

    // ── PnL history ──────────────────────────────────────────────────────

    @Test
    void recordPnlTracksSnapshots() {
        clock.set(1_700_200_000_000L);
        connection.recordPnl(5000L, -2000L);

        clock.advanceTo(1_700_300_000_000L);
        connection.recordPnl(7000L, 1000L);

        var pnlHistory = connection.getPnlHistory();
        assertEquals(2, pnlHistory.size());
        assertEquals(5000L, pnlHistory.get(0).realizedPnlPaisa());
        assertEquals(-2000L, pnlHistory.get(0).unrealizedPnlPaisa());
        assertEquals(7000L, pnlHistory.get(1).realizedPnlPaisa());
    }

    // ── Clock accessor ────────────────────────────────────────────────────

    @Test
    void clockReturnsSameClockInstance() {
        assertSame(clock, connection.clock());
    }

    // ── Initial cash ──────────────────────────────────────────────────────

    @Test
    void defaultInitialCashIsOneLakh() {
        SimulationPortfolioProvider portfolio = connection.portfolio();
        assertEquals(100_000_00L, portfolio.cashPaisa());
    }

    @Test
    void customInitialCashIsRespected() {
        BacktestBrokerConnection customConn = new BacktestBrokerConnection(clock, 500_000_00L);
        assertEquals(500_000_00L, customConn.portfolio().cashPaisa());
    }
}
