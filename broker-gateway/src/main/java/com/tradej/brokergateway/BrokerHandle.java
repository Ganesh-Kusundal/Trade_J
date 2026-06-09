package com.tradej.brokergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderBookSnapshotProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.brokergateway.explorer.BrokerExplorer;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.brokergateway.result.ResultMetadata;
import com.tradej.brokergateway.spi.BrokerExtras;
import com.tradej.brokergateway.spi.impl.DhanExtras;
import com.tradej.brokergateway.spi.impl.IciciExtras;
import com.tradej.brokergateway.spi.impl.UpstoxExtras;
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
import com.tradej.core.domain.instrument.IndexSymbols;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
public final class BrokerHandle extends BaseBrokerHandle {

    private volatile boolean rawCaptureEnabled = false;
    private volatile BrokerExtras cachedExtras;
    private volatile MarketDataHandle marketDataHandle;
    private volatile OrderHandle orderHandle;
    private volatile PortfolioHandle portfolioHandle;
    private volatile OptionsHandle optionsHandle;

    public BrokerHandle(BrokerSource source, IBrokerConnection connection) {
        super(source, connection);
    }

    public void enableRawCapture() {
        this.rawCaptureEnabled = true;
    }

    public void disableRawCapture() {
        this.rawCaptureEnabled = false;
    }

    public boolean isRawCaptureEnabled() {
        return rawCaptureEnabled;
    }

    @Override
    protected <T> GatewayResult<T> timed(TimedCall<T> call) {
        java.time.Instant start = java.time.Instant.now();
        T data = call.call();
        java.time.Duration latency = java.time.Duration.between(start, java.time.Instant.now());
        String rawBody = rawCaptureEnabled ? serializeRaw(data) : null;
        com.tradej.brokergateway.result.ResultMetadata metadata =
                new com.tradej.brokergateway.result.ResultMetadata(latency, java.time.Instant.now(), java.util.UUID.randomUUID().toString(), java.util.Map.of(), rawBody);
        return com.tradej.brokergateway.result.GatewayResult.success(data, source, metadata);
    }

    private String serializeRaw(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Market Data (MarketDataProvider) ─────────────────────────────

    public GatewayResult<Long> ltp(String symbol) {
        return ltp(symbol, defaultSegment(symbol));
    }

    public GatewayResult<Long> ltp(String symbol, ExchangeSegment segment) {
        return timed(() -> connection.marketData().getLtpPaisa(resolveKey(symbol, segment)));
    }

    public GatewayResult<Quote> quote(String symbol) {
        return quote(symbol, defaultSegment(symbol));
    }

    public GatewayResult<Quote> quote(String symbol, ExchangeSegment segment) {
        return timed(() -> connection.marketData().getQuote(resolveKey(symbol, segment)));
    }

    public GatewayResult<MarketDepth> depth(String symbol) {
        return depth(symbol, defaultSegment(symbol));
    }

    public GatewayResult<MarketDepth> depth(String symbol, ExchangeSegment segment) {
        return timed(() -> connection.marketData().getDepth(resolveKey(symbol, segment)));
    }

    public GatewayResult<Quote> ohlc(String symbol) {
        return ohlc(symbol, defaultSegment(symbol));
    }

    public GatewayResult<Quote> ohlc(String symbol, ExchangeSegment segment) {
        return timed(() -> connection.marketData().getOhlcSnapshot(resolveKey(symbol, segment)));
    }

    public GatewayResult<List<Candle>> historical(String symbol, String interval, LocalDate from, LocalDate to) {
        return historical(symbol, defaultSegment(symbol), interval, from, to);
    }

    public GatewayResult<List<Candle>> historical(String symbol, ExchangeSegment segment, String interval, LocalDate from, LocalDate to) {
        return timed(() -> connection.marketData().getCandles(
                new CandleHistoryRequest(resolveKey(symbol, segment), interval, from, to)));
    }

    public GatewayResult<Map<InstrumentKey, Long>> batchLtp(Collection<InstrumentKey> keys) {
        return timed(() -> connection.marketData().getLtpBatch(keys));
    }

    public GatewayResult<Map<InstrumentKey, Quote>> batchQuote(Collection<InstrumentKey> keys) {
        return timed(() -> connection.marketData().getQuoteBatch(keys));
    }

    public GatewayResult<Map<InstrumentKey, Quote>> batchOhlc(Collection<InstrumentKey> keys) {
        return timed(() -> connection.marketData().getOhlcBatch(keys));
    }

    // ── Options (OptionsProvider) ────────────────────────────────────

    public GatewayResult<List<LocalDate>> expiries(String underlying) {
        return expiries(underlying, ExchangeSegment.IDX_I);
    }

    public GatewayResult<List<LocalDate>> expiries(String underlying, ExchangeSegment segment) {
        return timed(() -> connection.options().getExpiries(underlying, segment));
    }

    public GatewayResult<OptionChainSnapshot> optionChain(String underlying) {
        List<LocalDate> expiries = connection.options().getExpiries(underlying, ExchangeSegment.IDX_I);
        if (expiries.isEmpty()) {
            Instrument inst = instruments.resolveNormalized(underlying, ExchangeSegment.IDX_I);
            return result(new OptionChainSnapshot(inst, null, 0L, List.of()));
        }
        return optionChain(underlying, ExchangeSegment.IDX_I, expiries.getFirst());
    }

    public GatewayResult<OptionChainSnapshot> optionChain(String underlying, ExchangeSegment segment, LocalDate expiry) {
        return timed(() -> connection.options().getOptionChain(underlying, segment, expiry));
    }

    public GatewayResult<OptionQuote> greeks(InstrumentKey key) {
        return timed(() -> connection.options().getGreeks(key));
    }

    public GatewayResult<List<Instrument>> optionContracts(String underlying, ExchangeSegment segment, LocalDate expiry) {
        return timed(() -> connection.options().getOptionContracts(underlying, segment, expiry));
    }

    public GatewayResult<Long> selectStrike(String underlying, ExchangeSegment segment,
                                            long spotPaisa, OptionType type,
                                            StrikeSelectionKind kind, int depth) {
        return timed(() -> connection.options().selectStrikePaisa(underlying, segment, spotPaisa, type, kind, depth));
    }

    public GatewayResult<RollingOptionSeries> rollingOptions(RollingOptionHistoryRequest request) {
        return timed(() -> connection.options().getExpiredOptionHistory(request));
    }

    // ── Portfolio (PortfolioProvider) ────────────────────────────────

    public GatewayResult<Balance> balance() {
        return timed(() -> connection.portfolio().getBalance());
    }

    public GatewayResult<List<Position>> positions() {
        return timed(() -> connection.portfolio().getPositions());
    }

    public GatewayResult<List<Holding>> holdings() {
        return timed(() -> connection.portfolio().getHoldings());
    }

    // ── Order Query (OrderQuery) ────────────────────────────────────

    public GatewayResult<List<Order>> orders() {
        return timed(() -> connection.orderQuery().getOrderBook());
    }

    public GatewayResult<Order> order(String orderId) {
        return timed(() -> connection.orderQuery().getOrder(orderId));
    }

    public GatewayResult<List<Trade>> trades() {
        return timed(() -> connection.orderQuery().getTradeBook());
    }

    // ── Order Command (OrderCommand) ────────────────────────────────

    public GatewayResult<Order> placeOrder(OrderRequest request) {
        return timed(() -> connection.orders().placeOrder(request));
    }

    public GatewayResult<Order> modifyOrder(ModifyOrderRequest request) {
        return timed(() -> connection.orders().modifyOrder(request));
    }

    public GatewayResult<Boolean> cancelOrder(String orderId) {
        return timed(() -> connection.orders().cancelOrder(orderId));
    }

    public GatewayResult<List<String>> cancelAllOpenOrders() {
        return timed(() -> connection.orders().cancelAllOpenOrders());
    }

    public GatewayResult<List<String>> cancelAndSquareOff() {
        return timed(() -> connection.orders().cancelAndSquareOffIntradayPositions());
    }

    public GatewayResult<Boolean> killSwitch(boolean enabled) {
        return timed(() -> connection.orders().setKillSwitch(enabled));
    }

    public GatewayResult<OrderPreview> previewOrder(OrderRequest request) {
        return timed(() -> connection.orders().previewOrder(request));
    }

    // ── Margin (MarginProvider) ─────────────────────────────────────

    public GatewayResult<MarginEstimate> estimateMargin(MarginEstimateRequest request) {
        return timed(() -> connection.margin().estimateMargin(request));
    }

    // ── Futures (FuturesProvider) ───────────────────────────────────

    public GatewayResult<List<Instrument>> futuresContracts(String underlying, ExchangeSegment segment) {
        return timed(() -> requireCapability(FuturesProvider.class, "futures")
                .getContracts(underlying, segment));
    }

    public GatewayResult<Instrument> nearestFutureContract(String underlying, ExchangeSegment segment) {
        return timed(() -> requireCapability(FuturesProvider.class, "futures")
                .getNearestContract(underlying, segment));
    }

    // ── Advanced Orders (capability-gated) ──────────────────────────

    public GatewayResult<Order> bracketOrder(OrderRequest request,
                                             long targetPaisa, long slPaisa, long trailingPaisa) {
        return timed(() -> requireCapability(BracketOrderProvider.class, "bracket orders")
                .placeSuperOrder(request, targetPaisa, slPaisa, trailingPaisa));
    }

    public GatewayResult<Order> gttOrder(OrderRequest request, String orderFlag,
                                         Long qty2, Long price2Paisa, Long trigger2Paisa) {
        return timed(() -> requireCapability(GttOrderProvider.class, "GTT orders")
                .placeForeverOrder(request, orderFlag, qty2, price2Paisa, trigger2Paisa));
    }

    public GatewayResult<List<Order>> sliceOrder(SliceOrderRequest request) {
        return timed(() -> requireCapability(SliceOrderCommand.class, "slice orders")
                .placeSliceOrder(request));
    }

    // ── News (capability-gated) ─────────────────────────────────────

    public GatewayResult<List<NewsArticle>> news(int page, int size) {
        return timed(() -> requireCapability(NewsProvider.class, "news")
                .getNewsForPositions(page, size));
    }

    // ── Alerts (capability-gated) ────────────────────────────────────

    public GatewayResult<String> placeAlert(ConditionalAlertRequest request) {
        return timed(() -> requireCapability(ConditionalAlertProvider.class, "alerts")
                .placeAlert(request));
    }

    public GatewayResult<ConditionalAlert> getAlert(String alertId) {
        return timed(() -> requireCapability(ConditionalAlertProvider.class, "alerts")
                .getAlert(alertId));
    }

    public GatewayResult<List<ConditionalAlert>> listAlerts() {
        return timed(() -> requireCapability(ConditionalAlertProvider.class, "alerts")
                .listAlerts());
    }

    public GatewayResult<Boolean> deleteAlert(String alertId) {
        return timed(() -> requireCapability(ConditionalAlertProvider.class, "alerts")
                .deleteAlert(alertId));
    }

    // ── Portfolio Summary (consolidated view) ────────────────────────

    /**
     * Returns a consolidated portfolio summary: balance + positions + holdings in one call.
     */
    public GatewayResult<PortfolioSummary> portfolioSummary() {
        return timed(() -> {
            Balance bal = connection.portfolio().getBalance();
            List<Position> pos = connection.portfolio().getPositions();
            List<Holding> hold = connection.portfolio().getHoldings();
            return new PortfolioSummary(bal, pos, hold);
        });
    }

    // ── WebSocket Status ─────────────────────────────────────────────

    public boolean isWebSocketConnected() {
        return connection.websocket().isConnected();
    }

    public GatewayResult<Map<com.tradej.broker.api.model.MarketSubscriptionRequest, com.tradej.core.domain.value.FeedMode>> subscriptions() {
        return timed(() -> connection.websocket().subscriptions());
    }

    public void connectWebSocket() {
        connection.websocket().connect();
    }

    public void disconnectWebSocket() {
        connection.websocket().disconnect();
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
        return orderBookSnapshot(symbol, defaultSegment(symbol), levels);
    }

    public GatewayResult<com.tradej.broker.core.depth.OrderBook.OrderBookSnapshot> orderBookSnapshot(
            String symbol, ExchangeSegment segment, int levels) {
        return timed(() -> connection.getCapability(OrderBookSnapshotProvider.class)
                .map(provider -> (com.tradej.broker.core.depth.OrderBook.OrderBookSnapshot) provider.snapshot(symbol, segment, levels))
                .orElse(null));
    }

    public GatewayResult<List<com.tradej.broker.core.depth.OrderBook.OrderBookSnapshot>> allOrderBookSnapshots(int levels) {
        return timed(() -> connection.getCapability(OrderBookSnapshotProvider.class)
                .map(provider -> provider.snapshotAll(levels).stream()
                        .map(s -> (com.tradej.broker.core.depth.OrderBook.OrderBookSnapshot) s)
                        .toList())
                .orElse(List.of()));
    }

    public GatewayResult<Map<String, ExchangeSegment>> activeOrderBookKeys() {
        return timed(() -> connection.getCapability(OrderBookSnapshotProvider.class)
                .map(OrderBookSnapshotProvider::activeBooks)
                .orElse(Map.of()));
    }

    // ── Capabilities ────────────────────────────────────────────────

    public boolean supports(Class<?> capability) {
        return connection.getCapability(capability).isPresent();
    }

    public IBrokerConnection connection() {
        return connection;
    }

    public BrokerSource source() {
        return source;
    }

    public int instrumentCount() {
        return instruments.catalogSize();
    }

    public BrokerExtras extras() {
        if (cachedExtras != null) return cachedExtras;
        cachedExtras = switch (source) {
            case DHAN -> new DhanExtras(connection);
            case UPSTOX -> new UpstoxExtras(connection);
            case ICICI -> new IciciExtras(connection);
            default -> new BrokerExtras() {
                @Override public BrokerSource source() { return source; }
            };
        };
        return cachedExtras;
    }

    /**
     * Dynamic dispatch — invoke any BrokerHandle method by name.
     * Supports: ltp, quote, depth, ohlc, historical, expiries, optionChain,
     * balance, positions, holdings, orders, trades, news, listAlerts,
     * instrumentCount, isWebSocketConnected.
     *
     * @param method the method name to invoke
     * @param params method-specific parameters
     * @return the result wrapped in a GatewayResult, or empty if method not found
     */
    public Optional<Object> invoke(String method, Map<String, Object> params) {
        return switch (method) {
            case "ltp" -> Optional.of(ltp(
                    (String) params.get("symbol"),
                    parseSegment(params, "segment")));
            case "quote" -> Optional.of(quote(
                    (String) params.get("symbol"),
                    parseSegment(params, "segment")));
            case "depth" -> Optional.of(depth(
                    (String) params.get("symbol"),
                    parseSegment(params, "segment")));
            case "ohlc" -> Optional.of(ohlc(
                    (String) params.get("symbol"),
                    parseSegment(params, "segment")));
            case "balance" -> Optional.of(balance());
            case "positions" -> Optional.of(positions());
            case "holdings" -> Optional.of(holdings());
            case "orders" -> Optional.of(orders());
            case "trades" -> Optional.of(trades());
            case "instrumentCount" -> Optional.of(instrumentCount());
            case "isWebSocketConnected" -> Optional.of(isWebSocketConnected());
            case "extras" -> Optional.of(extras());
            case "capabilities" -> Optional.of(capabilities());
            case "getOptionGreeks", "optionGreeks" -> Optional.of(greeks(parseInstrumentKey(params)));
            case "orderBookSnapshot" -> Optional.of(orderBookSnapshot(
                    (String) params.get("symbol"),
                    parseSegment(params, "segment"),
                    parseLevels(params)));
            case "allOrderBookSnapshots" -> Optional.of(allOrderBookSnapshots(parseLevels(params)));
            case "activeOrderBookKeys" -> Optional.of(activeOrderBookKeys());
            default -> extras().invoke(method, params);
        };
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
        if (marketDataHandle == null) marketDataHandle = new MarketDataHandle(source, connection);
        return marketDataHandle;
    }

    public OrderHandle orderHandle() {
        if (orderHandle == null) orderHandle = new OrderHandle(source, connection);
        return orderHandle;
    }

    public PortfolioHandle portfolioHandle() {
        if (portfolioHandle == null) portfolioHandle = new PortfolioHandle(source, connection);
        return portfolioHandle;
    }

    public OptionsHandle optionsHandle() {
        if (optionsHandle == null) optionsHandle = new OptionsHandle(source, connection);
        return optionsHandle;
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

    // ── Internal ────────────────────────────────────────────────────

    private InstrumentKey parseInstrumentKey(Map<String, Object> params) {
        Object rawKey = params.get("instrumentKey");
        if (rawKey instanceof InstrumentKey ik) return ik;
        String symbol = (String) params.get("symbol");
        if (symbol == null) {
            throw new IllegalArgumentException("invoke requires 'symbol' or 'instrumentKey' in params");
        }
        return resolveKey(symbol, parseSegment(params, "segment"));
    }
}
