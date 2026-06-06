package com.tradej.brokergateway.simulation;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link IBrokerConnection} implementation for backtesting.
 * Uses virtual time via {@link BrokerClock} and tracks all trades for P&amp;L analysis.
 *
 * <p>Key differences from {@link PaperBrokerConnection}:
 * <ul>
 *   <li>Uses a {@link BrokerClock} instead of wall-clock time</li>
 *   <li>Tracks all placed orders for P&amp;L calculation</li>
 *   <li>Provides {@link #getTradeHistory()} and {@link #getPnlHistory()} for backtest result analysis</li>
 *   <li>Does NOT connect to any real broker</li>
 * </ul>
 */
public final class BacktestBrokerConnection implements IBrokerConnection {

    private final BrokerClock clock;
    private final SimulatedMarketDataProvider marketData;
    private final SimulationPortfolioProvider portfolio;
    private final SimulatedWebSocketMultiplexer websocket;
    private final CopyOnWriteArrayList<TradeRecord> tradeHistory = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<PnlRecord> pnlHistory = new CopyOnWriteArrayList<>();
    private final AtomicLong orderSeq = new AtomicLong(1000);

    public BacktestBrokerConnection(BrokerClock clock) {
        this(clock, 100_000_00L);
    }

    public BacktestBrokerConnection(BrokerClock clock, long initialCashPaisa) {
        this.clock = clock;
        this.marketData = new SimulatedMarketDataProvider();
        this.portfolio = new SimulationPortfolioProvider(initialCashPaisa);
        this.websocket = new SimulatedWebSocketMultiplexer();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getCapability(Class<T> capabilityClass) {
        if (capabilityClass == MarketDataProvider.class) return Optional.of((T) marketData);
        if (capabilityClass == PortfolioProvider.class) return Optional.of((T) portfolio);
        if (capabilityClass == WebSocketMultiplexer.class) return Optional.of((T) websocket);
        if (capabilityClass == InstrumentResolver.class) return Optional.of((T) backtestInstrumentResolver);
        if (capabilityClass == OrderCommand.class) return Optional.of((T) backtestOrderCommand);
        if (capabilityClass == OrderQuery.class) return Optional.of((T) backtestOrderQuery);
        return Optional.empty();
    }

    @Override public void connect() {}
    @Override public void disconnect() {}
    @Override public void loadInstrumentCatalog(Path path) {}

    // ── Backtest-specific accessors ────────────────────────────────────

    /** Returns the virtual clock used by this backtest connection. */
    public BrokerClock clock() { return clock; }

    /** Returns an immutable copy of all trades placed during the backtest. */
    public List<TradeRecord> getTradeHistory() { return List.copyOf(tradeHistory); }

    /** Returns an immutable copy of all P&amp;L snapshots recorded during the backtest. */
    public List<PnlRecord> getPnlHistory() { return List.copyOf(pnlHistory); }

    /** Returns the underlying simulated market data provider. */
    public SimulatedMarketDataProvider marketData() { return marketData; }

    /** Returns the underlying simulated portfolio provider. */
    public SimulationPortfolioProvider portfolio() { return portfolio; }

    /**
     * Records a P&amp;L snapshot at the current virtual time.
     * Call this periodically during backtesting to track equity curve.
     *
     * @param realizedPnlPaisa   cumulative realized P&amp;L in paisa
     * @param unrealizedPnlPaisa current unrealized P&amp;L in paisa
     */
    public void recordPnl(long realizedPnlPaisa, long unrealizedPnlPaisa) {
        pnlHistory.add(new PnlRecord(realizedPnlPaisa, unrealizedPnlPaisa, clock.currentTimeMs()));
    }

    // ── Records ────────────────────────────────────────────────────────

    /** Record of a single order fill during backtesting. */
    public record TradeRecord(
            String orderId,
            String symbol,
            String side,
            long quantity,
            long pricePaisa,
            long timestampMs
    ) {}

    /** Point-in-time P&amp;L snapshot during backtesting. */
    public record PnlRecord(
            long realizedPnlPaisa,
            long unrealizedPnlPaisa,
            long timestampMs
    ) {}

    // ── InstrumentResolver ─────────────────────────────────────────────

    private final InstrumentResolver backtestInstrumentResolver = new InstrumentResolver() {
        @Override public Instrument resolve(InstrumentKey key) { return toInstrument(key); }
        @Override public Instrument getBySymbol(InstrumentKey key) { return toInstrument(key); }

        @Override
        public Instrument resolveNormalized(String symbol, ExchangeSegment seg) {
            return toInstrument(new InstrumentKey(symbol, seg));
        }

        @Override public List<Instrument> allInstruments() { return List.of(); }

        @Override
        public Instrument resolveBySecurityId(String id) {
            return toInstrument(new InstrumentKey(id, ExchangeSegment.NSE_EQ));
        }

        @Override public Instrument requireDefinition(InstrumentKey key) { return toInstrument(key); }

        @Override
        public Instrument resolvePayload(Object payload) {
            throw new UnsupportedOperationException("Backtest does not support payload resolution");
        }

        @Override public boolean isLoaded() { return true; }
        @Override public int catalogSize() { return 8; }
    };

    // ── OrderCommand ───────────────────────────────────────────────────

    private final OrderCommand backtestOrderCommand = new OrderCommand() {
        @Override
        public Order placeOrder(OrderRequest request) {
            String id = "BT-" + orderSeq.incrementAndGet();
            long fillPrice = request.pricePaisa() > 0
                    ? request.pricePaisa()
                    : marketData.getLtpPaisa(new InstrumentKey(request.symbol(), request.exchangeSegment()));

            tradeHistory.add(new TradeRecord(
                    id, request.symbol(), request.side().name(),
                    request.quantity(), fillPrice, clock.currentTimeMs()));

            return new Order(
                    id, request.correlationId(), request.symbol(), request.exchangeSegment(),
                    request.side(), request.productType(), request.orderType(),
                    OrderStatus.TRADED, request.quantity(), request.quantity(),
                    fillPrice, request.triggerPricePaisa(), clock.currentTimeMs(), null);
        }

        @Override
        public Order modifyOrder(ModifyOrderRequest request) {
            throw new UnsupportedOperationException("Backtest does not support order modification");
        }

        @Override public boolean cancelOrder(String orderId) { return false; }
        @Override public List<String> cancelAllOpenOrders() { return List.of(); }
        @Override public List<String> cancelAndSquareOffIntradayPositions() { return List.of(); }
        @Override public boolean setKillSwitch(boolean enabled) { return true; }

        @Override
        public OrderPreview previewOrder(OrderRequest request) {
            long notional = request.quantity() * (request.pricePaisa() > 0 ? request.pricePaisa() : 100_000L);
            return OrderPreview.valid(
                    request.symbol(), request.exchangeSegment(), request.side(),
                    request.quantity(), request.pricePaisa(), request.triggerPricePaisa(),
                    request.productType(), notional, notional / 5);
        }
    };

    // ── OrderQuery ─────────────────────────────────────────────────────

    private final OrderQuery backtestOrderQuery = new OrderQuery() {
        @Override public Order getOrder(String orderId) { return null; }
        @Override public List<Order> getOrderBook() { return List.of(); }
        @Override public List<Trade> getTradeBook() { return List.of(); }

        @Override
        public OrderStatus getOrderStatus(String orderId) {
            return OrderStatus.TRADED;
        }

        @Override
        public OptionalLong getExecutedPricePaisa(String orderId) {
            return tradeHistory.stream()
                    .filter(t -> t.orderId().equals(orderId))
                    .findFirst()
                    .map(t -> OptionalLong.of(t.pricePaisa()))
                    .orElse(OptionalLong.empty());
        }

        @Override
        public OptionalLong getExchangeTimeMs(String orderId) {
            return tradeHistory.stream()
                    .filter(t -> t.orderId().equals(orderId))
                    .findFirst()
                    .map(t -> OptionalLong.of(t.timestampMs()))
                    .orElse(OptionalLong.empty());
        }
    };

    // ── Helpers ────────────────────────────────────────────────────────

    private static Instrument toInstrument(InstrumentKey key) {
        return new Instrument(
                key.symbol(), key.symbol(),
                key.exchangeSegment().exchange(), key.exchangeSegment(),
                "EQ", null, null, null, null, 1L, 5L);
    }
}
