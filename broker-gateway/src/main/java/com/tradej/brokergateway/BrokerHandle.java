package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OrderBookSnapshotProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.brokergateway.explorer.BrokerExplorer;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.ConditionalAlert;
import com.tradej.core.domain.model.ConditionalAlertRequest;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.NewsArticle;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.RollingOptionSeries;
import com.tradej.core.domain.model.SliceOrderRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Fluent API for interacting with a single broker.
 * Every operation returns a {@link GatewayResult} with latency and source metadata.
 *
 * <p>Covers all 18 port interfaces from broker-api:
 * MarketDataProvider, OptionsProvider, OrderCommand, OrderQuery, PortfolioProvider,
 * MarginProvider, InstrumentResolver, WebSocketMultiplexer, FuturesProvider,
 * BracketOrderProvider, GttOrderProvider, SliceOrderCommand, SessionRiskProvider,
 * ConditionalAlertProvider, NewsProvider, and capability markers.
 *
 * <p>Usage:
 * <pre>
 *   BrokerHandle dhan = gateway.broker("dhan");
 *   GatewayResult&lt;Quote&gt; result = dhan.quote("RELIANCE");
 *   System.out.println("LTP=" + result.data().ltpPaisa() + " latency=" + result.latencyMs() + "ms");
 * </pre>
 */
public final class BrokerHandle {

    private final BrokerCallSupport support;

    private volatile MarketDataHandle marketDataHandle;
    private volatile OrderHandle orderHandle;
    private volatile PortfolioHandle portfolioHandle;
    private volatile OptionsHandle optionsHandle;

    public BrokerHandle(BrokerSource source, IBrokerConnection connection) {
        this.support = new BrokerCallSupport(source, connection);
    }

    // ── Market Data (MarketDataProvider) ─────────────────────────────

    public GatewayResult<Long> ltp(String symbol) {
        return ltp(symbol, support.defaultSegment(symbol));
    }

    public GatewayResult<Long> ltp(String symbol, ExchangeSegment segment) {
        return support.timed(() -> support.connection().marketData().getLtpPaisa(support.resolveKey(symbol, segment)));
    }

    public GatewayResult<Quote> quote(String symbol) {
        return quote(symbol, support.defaultSegment(symbol));
    }

    public GatewayResult<Quote> quote(String symbol, ExchangeSegment segment) {
        return support.timed(() -> support.connection().marketData().getQuote(support.resolveKey(symbol, segment)));
    }

    public GatewayResult<MarketDepth> depth(String symbol) {
        return depth(symbol, support.defaultSegment(symbol));
    }

    public GatewayResult<MarketDepth> depth(String symbol, ExchangeSegment segment) {
        return support.timed(() -> support.connection().marketData().getDepth(support.resolveKey(symbol, segment)));
    }

    public GatewayResult<Quote> ohlc(String symbol) {
        return ohlc(symbol, support.defaultSegment(symbol));
    }

    public GatewayResult<Quote> ohlc(String symbol, ExchangeSegment segment) {
        return support.timed(() -> support.connection().marketData().getOhlcSnapshot(support.resolveKey(symbol, segment)));
    }

    public GatewayResult<List<Candle>> historical(String symbol, String interval, LocalDate from, LocalDate to) {
        return historical(symbol, support.defaultSegment(symbol), interval, from, to);
    }

    public GatewayResult<List<Candle>> historical(String symbol, ExchangeSegment segment, String interval, LocalDate from, LocalDate to) {
        return support.timed(() -> support.connection().marketData().getCandles(
                new CandleHistoryRequest(support.resolveKey(symbol, segment), interval, from, to)));
    }

    public GatewayResult<Map<InstrumentKey, Long>> batchLtp(Collection<InstrumentKey> keys) {
        return support.timed(() -> support.connection().marketData().getLtpBatch(keys));
    }

    public GatewayResult<Map<InstrumentKey, Quote>> batchQuote(Collection<InstrumentKey> keys) {
        return support.timed(() -> support.connection().marketData().getQuoteBatch(keys));
    }

    public GatewayResult<Map<InstrumentKey, Quote>> batchOhlc(Collection<InstrumentKey> keys) {
        return support.timed(() -> support.connection().marketData().getOhlcBatch(keys));
    }

    // ── Options (OptionsProvider) ────────────────────────────────────

    public GatewayResult<List<LocalDate>> expiries(String underlying) {
        return expiries(underlying, ExchangeSegment.IDX_I);
    }

    public GatewayResult<List<LocalDate>> expiries(String underlying, ExchangeSegment segment) {
        return support.timed(() -> support.connection().options().getExpiries(underlying, segment));
    }

    public GatewayResult<OptionChainSnapshot> optionChain(String underlying) {
        List<LocalDate> expiries = support.connection().options().getExpiries(underlying, ExchangeSegment.IDX_I);
        if (expiries.isEmpty()) {
            Instrument inst = support.instruments().resolveNormalized(underlying, ExchangeSegment.IDX_I);
            return support.result(new OptionChainSnapshot(inst, null, 0L, List.of()));
        }
        return optionChain(underlying, ExchangeSegment.IDX_I, expiries.getFirst());
    }

    public GatewayResult<OptionChainSnapshot> optionChain(String underlying, ExchangeSegment segment, LocalDate expiry) {
        return support.timed(() -> support.connection().options().getOptionChain(underlying, segment, expiry));
    }

    public GatewayResult<OptionQuote> greeks(InstrumentKey key) {
        return support.timed(() -> support.connection().options().getGreeks(key));
    }

    public GatewayResult<List<Instrument>> optionContracts(String underlying, ExchangeSegment segment, LocalDate expiry) {
        return support.timed(() -> support.connection().options().getOptionContracts(underlying, segment, expiry));
    }

    public GatewayResult<Long> selectStrike(String underlying, ExchangeSegment segment,
                                            long spotPaisa, OptionType type,
                                            StrikeSelectionKind kind, int depth) {
        return support.timed(() -> support.connection().options().selectStrikePaisa(underlying, segment, spotPaisa, type, kind, depth));
    }

    public GatewayResult<RollingOptionSeries> rollingOptions(RollingOptionHistoryRequest request) {
        return support.timed(() -> support.connection().options().getExpiredOptionHistory(request));
    }

    // ── Portfolio (PortfolioProvider) ────────────────────────────────

    public GatewayResult<Balance> balance() {
        return support.timed(() -> support.connection().portfolio().getBalance());
    }

    public GatewayResult<List<Position>> positions() {
        return support.timed(() -> support.connection().portfolio().getPositions());
    }

    public GatewayResult<List<Holding>> holdings() {
        return support.timed(() -> support.connection().portfolio().getHoldings());
    }

    // ── Order Query (OrderQuery) ────────────────────────────────────

    public GatewayResult<List<Order>> orders() {
        return support.timed(() -> support.connection().orderQuery().getOrderBook());
    }

    public GatewayResult<Order> order(String orderId) {
        return support.timed(() -> support.connection().orderQuery().getOrder(orderId));
    }

    public GatewayResult<List<Trade>> trades() {
        return support.timed(() -> support.connection().orderQuery().getTradeBook());
    }

    // ── Order Command (OrderCommand) ────────────────────────────────

    public GatewayResult<Order> placeOrder(OrderRequest request) {
        return support.timed(() -> support.connection().orders().placeOrder(request));
    }

    public GatewayResult<Order> modifyOrder(ModifyOrderRequest request) {
        return support.timed(() -> support.connection().orders().modifyOrder(request));
    }

    public GatewayResult<Boolean> cancelOrder(String orderId) {
        return support.timed(() -> support.connection().orders().cancelOrder(orderId));
    }

    public GatewayResult<List<String>> cancelAllOpenOrders() {
        return support.timed(() -> support.connection().orders().cancelAllOpenOrders());
    }

    public GatewayResult<List<String>> cancelAndSquareOff() {
        return support.timed(() -> support.connection().orders().cancelAndSquareOffIntradayPositions());
    }

    public GatewayResult<Boolean> killSwitch(boolean enabled) {
        return support.timed(() -> support.connection().orders().setKillSwitch(enabled));
    }

    public GatewayResult<OrderPreview> previewOrder(OrderRequest request) {
        return support.timed(() -> support.connection().orders().previewOrder(request));
    }

    // ── Margin (MarginProvider) ─────────────────────────────────────

    public GatewayResult<MarginEstimate> estimateMargin(MarginEstimateRequest request) {
        return support.timed(() -> support.connection().margin().estimateMargin(request));
    }

    // ── Futures (FuturesProvider) ───────────────────────────────────

    public GatewayResult<List<Instrument>> futuresContracts(String underlying, ExchangeSegment segment) {
        return support.timed(() -> support.requireCapability(FuturesProvider.class, "futures")
                .getContracts(underlying, segment));
    }

    public GatewayResult<Instrument> nearestFutureContract(String underlying, ExchangeSegment segment) {
        return support.timed(() -> support.requireCapability(FuturesProvider.class, "futures")
                .getNearestContract(underlying, segment));
    }

    // ── Advanced Orders (capability-gated) ──────────────────────────

    public GatewayResult<Order> bracketOrder(OrderRequest request,
                                             long targetPaisa, long slPaisa, long trailingPaisa) {
        return support.timed(() -> support.requireCapability(BracketOrderProvider.class, "bracket orders")
                .placeSuperOrder(request, targetPaisa, slPaisa, trailingPaisa));
    }

    public GatewayResult<Order> gttOrder(OrderRequest request, String orderFlag,
                                         Long qty2, Long price2Paisa, Long trigger2Paisa) {
        return support.timed(() -> support.requireCapability(GttOrderProvider.class, "GTT orders")
                .placeForeverOrder(request, orderFlag, qty2, price2Paisa, trigger2Paisa));
    }

    public GatewayResult<List<Order>> sliceOrder(SliceOrderRequest request) {
        return support.timed(() -> support.requireCapability(SliceOrderCommand.class, "slice orders")
                .placeSliceOrder(request));
    }

    // ── News (capability-gated) ─────────────────────────────────────

    public GatewayResult<List<NewsArticle>> news(int page, int size) {
        return support.timed(() -> support.requireCapability(NewsProvider.class, "news")
                .getNewsForPositions(page, size));
    }

    // ── Alerts (capability-gated) ────────────────────────────────────

    public GatewayResult<String> placeAlert(ConditionalAlertRequest request) {
        return support.timed(() -> support.requireCapability(ConditionalAlertProvider.class, "alerts")
                .placeAlert(request));
    }

    public GatewayResult<ConditionalAlert> getAlert(String alertId) {
        return support.timed(() -> support.requireCapability(ConditionalAlertProvider.class, "alerts")
                .getAlert(alertId));
    }

    public GatewayResult<List<ConditionalAlert>> listAlerts() {
        return support.timed(() -> support.requireCapability(ConditionalAlertProvider.class, "alerts")
                .listAlerts());
    }

    public GatewayResult<Boolean> deleteAlert(String alertId) {
        return support.timed(() -> support.requireCapability(ConditionalAlertProvider.class, "alerts")
                .deleteAlert(alertId));
    }

    // ── Portfolio Summary (consolidated view) ────────────────────────

    /**
     * Returns a consolidated portfolio summary: balance + positions + holdings in one call.
     */
    public GatewayResult<PortfolioSummary> portfolioSummary() {
        return support.timed(() -> {
            Balance bal = support.connection().portfolio().getBalance();
            List<Position> pos = support.connection().portfolio().getPositions();
            List<Holding> hold = support.connection().portfolio().getHoldings();
            return new PortfolioSummary(bal, pos, hold);
        });
    }

    // ── WebSocket Status ─────────────────────────────────────────────

    public boolean isWebSocketConnected() {
        return support.connection().websocket().isConnected();
    }

    public GatewayResult<Map<com.tradej.broker.api.model.MarketSubscriptionRequest, com.tradej.core.domain.value.FeedMode>> subscriptions() {
        return support.timed(() -> support.connection().websocket().subscriptions());
    }

    public void connectWebSocket() {
        support.connection().websocket().connect();
    }

    public void disconnectWebSocket() {
        support.connection().websocket().disconnect();
    }

    // ── Order Book Snapshots (broker-internal SPI) ───────────────────

    /**
     * Fetch the live L2 order book snapshot for a single instrument.
     * Returns {@code null} data if the broker does not maintain a book
     * for this symbol (no depth subscription yet, or symbol unknown).
     *
     * <p>Tagged as a broker-internal projection; outside callers should
     * prefer the shared {@code OrderBookEngine} via the gateway REST
     * controller ({@code /api/v1/market/depth/{symbol}}).
     */
    public GatewayResult<com.tradej.broker.core.depth.OrderBook.OrderBookSnapshot> orderBookSnapshot(
            String symbol, int levels) {
        return orderBookSnapshot(symbol, support.defaultSegment(symbol), levels);
    }

    public GatewayResult<com.tradej.broker.core.depth.OrderBook.OrderBookSnapshot> orderBookSnapshot(
            String symbol, ExchangeSegment segment, int levels) {
        return support.timed(() -> support.capabilityRouter().find(OrderBookSnapshotProvider.class)
                .map(provider -> (com.tradej.broker.core.depth.OrderBook.OrderBookSnapshot) provider.snapshot(symbol, segment, levels))
                .orElse(null));
    }

    public GatewayResult<List<com.tradej.broker.core.depth.OrderBook.OrderBookSnapshot>> allOrderBookSnapshots(int levels) {
        return support.timed(() -> support.capabilityRouter().find(OrderBookSnapshotProvider.class)
                .map(provider -> provider.snapshotAll(levels).stream()
                        .map(s -> (com.tradej.broker.core.depth.OrderBook.OrderBookSnapshot) s)
                        .toList())
                .orElse(List.of()));
    }

    public GatewayResult<Map<String, ExchangeSegment>> activeOrderBookKeys() {
        return support.timed(() -> support.capabilityRouter().find(OrderBookSnapshotProvider.class)
                .map(OrderBookSnapshotProvider::activeBooks)
                .orElse(Map.of()));
    }

    // ── Capabilities ────────────────────────────────────────────────

    public boolean supports(Class<?> capability) {
        return support.capabilityRouter().supports(capability);
    }

    public IBrokerConnection connection() {
        return support.connection();
    }

    public BrokerSource source() {
        return support.source();
    }

    public int instrumentCount() {
        return support.instruments().catalogSize();
    }

    /**
     * Returns a map of all capability class names to their support status.
     */
    public Map<String, Boolean> capabilities() {
        // delegate to BrokerExplorer for full capability map
        return BrokerExplorer.inspect(this).capabilities();
    }

    // ── Per-Port Handle Accessors ─────────────────────────────────

    public MarketDataHandle marketDataHandle() {
        MarketDataHandle h = marketDataHandle;
        if (h == null) {
            synchronized (this) {
                h = marketDataHandle;
                if (h == null) {
                    h = new MarketDataHandle(support.source(), support.connection());
                    marketDataHandle = h;
                }
            }
        }
        return h;
    }

    public OrderHandle orderHandle() {
        OrderHandle h = orderHandle;
        if (h == null) {
            synchronized (this) {
                h = orderHandle;
                if (h == null) {
                    h = new OrderHandle(support.source(), support.connection());
                    orderHandle = h;
                }
            }
        }
        return h;
    }

    public PortfolioHandle portfolioHandle() {
        PortfolioHandle h = portfolioHandle;
        if (h == null) {
            synchronized (this) {
                h = portfolioHandle;
                if (h == null) {
                    h = new PortfolioHandle(support.source(), support.connection());
                    portfolioHandle = h;
                }
            }
        }
        return h;
    }

    public OptionsHandle optionsHandle() {
        OptionsHandle h = optionsHandle;
        if (h == null) {
            synchronized (this) {
                h = optionsHandle;
                if (h == null) {
                    h = new OptionsHandle(support.source(), support.connection());
                    optionsHandle = h;
                }
            }
        }
        return h;
    }

    // ── Types ────────────────────────────────────────────────────────

    /**
     * Consolidated portfolio snapshot: balance, positions, and holdings.
     */
    public record PortfolioSummary(
            Balance balance,
            List<Position> positions,
            List<Holding> holdings
    ) {
        public int positionCount() {
            return positions == null ? 0 : positions.size();
        }

        public int holdingCount() {
            return holdings == null ? 0 : holdings.size();
        }

        public long cashPaisa() {
            return balance != null ? balance.cashPaisa() : 0;
        }
    }
}
