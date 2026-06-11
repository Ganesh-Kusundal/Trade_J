package com.tradej.brokergateway.simulation;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.OrderUpdateListener;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.instrument.IndexSymbols;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionGreeks;
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
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class PaperBrokerConnection implements IBrokerConnection {

    private final long initialCashPaisa;
    private final Map<String, Long> basePricesPaisa;
    private final Map<String, Order> activeOrders;
    private final Map<Class<?>, Object> capabilityMap;
    private static final AtomicLong orderSeq = new AtomicLong(1000);

    public PaperBrokerConnection() {
        this(100_000_00L);
    }

    public PaperBrokerConnection(long initialCashPaisa) {
        this.initialCashPaisa = initialCashPaisa;
        this.basePricesPaisa = new HashMap<>();
        this.activeOrders = new ConcurrentHashMap<>();

        basePricesPaisa.put("RELIANCE", 250_000L);
        basePricesPaisa.put("TCS", 380_000L);
        basePricesPaisa.put("HDFCBANK", 165_000L);
        basePricesPaisa.put("INFY", 155_000L);
        basePricesPaisa.put("SBIN", 75_000L);
        basePricesPaisa.put(IndexSymbols.NIFTY, 2400_000L);
        basePricesPaisa.put(IndexSymbols.NIFTY_BANK, 5100_000L);
        basePricesPaisa.put(IndexSymbols.NIFTY_FIN_SERVICE, 2300_000L);

        this.marketDataProvider = new SimulatedMarketDataProvider(basePricesPaisa);
        this.portfolioProvider = new SimulationPortfolioProvider(initialCashPaisa);
        this.capabilityMap = buildCapabilityMap();
    }

    private long basePrice(String symbol) {
        return basePricesPaisa.getOrDefault(IndexSymbols.canonicalize(symbol), 100_000L);
    }

    private long jitter(long basePaisa) {
        double pct = ThreadLocalRandom.current().nextDouble(-0.005, 0.005);
        return basePaisa + (long) (basePaisa * pct);
    }

    @Override
    public com.tradej.broker.api.spi.BrokerSource source() {
        return com.tradej.broker.api.spi.BrokerSource.SIMULATION;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getCapability(Class<T> capabilityClass) {
        if (capabilityClass == null) {
            return Optional.empty();
        }
        Object impl = capabilityMap.get(capabilityClass);
        if (impl != null && capabilityClass.isInstance(impl)) {
            return Optional.of((T) impl);
        }
        // Fallback: scan all registered implementations (handles concrete-type lookups
        // and multi-interface implementations)
        for (Object value : capabilityMap.values()) {
            if (capabilityClass.isInstance(value)) {
                return Optional.of((T) value);
            }
        }
        return Optional.empty();
    }

    private Map<Class<?>, Object> buildCapabilityMap() {
        Map<Class<?>, Object> map = new LinkedHashMap<>();
        map.put(MarketDataProvider.class, marketDataProvider);
        map.put(OptionsProvider.class, optionsProvider);
        map.put(OrderCommand.class, orderCommand);
        map.put(OrderQuery.class, orderQuery);
        map.put(PortfolioProvider.class, portfolioProvider);
        map.put(MarginProvider.class, marginProvider);
        map.put(InstrumentResolver.class, instrumentResolver);
        map.put(FuturesProvider.class, futuresProvider);
        map.put(SliceOrderCommand.class, sliceOrderCommand);
        map.put(WebSocketMultiplexer.class, wsMultiplexer);
        return map;
    }

    @Override public void connect() { }
    @Override public void disconnect() { activeOrders.clear(); }
    @Override public void loadInstrumentCatalog(Path catalogPath) { }

    private final MarketDataProvider marketDataProvider;

    // ── OptionsProvider ───────────────────────────────────────────────

    private final OptionsProvider optionsProvider = new OptionsProvider() {
        @Override
        public List<LocalDate> getExpiries(String underlying, ExchangeSegment segment) {
            LocalDate now = LocalDate.now();
            List<LocalDate> expiries = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                expiries.add(now.withDayOfMonth(25).plusMonths(i));
            }
            return expiries;
        }

        @Override
        public OptionChainSnapshot getOptionChain(String underlying, ExchangeSegment segment, LocalDate expiry) {
            long spot = basePrice(underlying);
            Instrument inst = toInstrument(new InstrumentKey(underlying, segment));
            return new OptionChainSnapshot(inst, expiry, spot, List.of());
        }

        @Override
        public OptionQuote getGreeks(InstrumentKey key) {
            return new OptionQuote(toInstrument(key), 500_00L, 100_000L, 50_000L,
                    490_00L, 500L, 510_00L, 500L,
                    new OptionGreeks(0.55, -0.02, 0.01, 25.0, 0.18));
        }

        @Override
        public List<Instrument> getOptionContracts(String underlying, ExchangeSegment segment, LocalDate expiry) {
            return List.of();
        }

        @Override
        public long selectStrikePaisa(String underlying, ExchangeSegment segment, long spotPaisa,
                                      OptionType type, StrikeSelectionKind kind, int depth) {
            return spotPaisa;
        }

        @Override
        public RollingOptionSeries getExpiredOptionHistory(RollingOptionHistoryRequest request) {
            return new RollingOptionSeries(request, List.of());
        }
    };

    // ── OrderCommand ──────────────────────────────────────────────────

    private final OrderCommand orderCommand = new OrderCommand() {
        @Override
        public Order placeOrder(OrderRequest request) {
            String id = "SIM-" + orderSeq.incrementAndGet();
            Order order = new Order(id, null, request.symbol(), request.exchangeSegment(),
                    request.side(), request.productType(), request.orderType(),
                    OrderStatus.TRADED, request.quantity(), request.quantity(),
                    request.pricePaisa(), request.triggerPricePaisa(),
                    System.currentTimeMillis(), null);
            activeOrders.put(id, order);
            return order;
        }

        @Override
        public Order modifyOrder(ModifyOrderRequest request) {
            Order existing = activeOrders.get(request.orderId());
            if (existing != null) {
                return existing;
            }
            return new Order(request.orderId(), null, "UNKNOWN", ExchangeSegment.NSE_EQ,
                    com.tradej.core.domain.value.Side.BUY,
                    com.tradej.core.domain.value.ProductType.INTRADAY,
                    com.tradej.core.domain.value.OrderType.LIMIT,
                    OrderStatus.OPEN, 0, 0, 0, 0,
                    System.currentTimeMillis(), null);
        }

        @Override
        public boolean cancelOrder(String orderId) {
            return activeOrders.remove(orderId) != null;
        }

        @Override
        public List<String> cancelAllOpenOrders() {
            List<String> ids = new ArrayList<>(activeOrders.keySet());
            activeOrders.clear();
            return ids;
        }

        @Override
        public List<String> cancelAndSquareOffIntradayPositions() {
            return cancelAllOpenOrders();
        }

        @Override
        public boolean setKillSwitch(boolean enabled) {
            return true;
        }

        @Override
        public OrderPreview previewOrder(OrderRequest request) {
            long notional = request.quantity() * request.pricePaisa();
            long margin = notional / 5;
            return OrderPreview.valid(request.symbol(), request.exchangeSegment(),
                    request.side(), request.quantity(), request.pricePaisa(),
                    request.triggerPricePaisa(), request.productType(), notional, margin);
        }
    };

    // ── OrderQuery ────────────────────────────────────────────────────

    private final OrderQuery orderQuery = new OrderQuery() {
        @Override
        public List<Order> getOrderBook() {
            return new ArrayList<>(activeOrders.values());
        }

        @Override
        public Order getOrder(String orderId) {
            return activeOrders.get(orderId);
        }

        @Override
        public List<Trade> getTradeBook() {
            return List.of();
        }

        @Override
        public OrderStatus getOrderStatus(String orderId) {
            Order o = activeOrders.get(orderId);
            return o != null ? o.status() : OrderStatus.REJECTED;
        }

        @Override
        public OptionalLong getExecutedPricePaisa(String orderId) {
            Order o = activeOrders.get(orderId);
            return o != null ? OptionalLong.of(o.pricePaisa()) : OptionalLong.empty();
        }

        @Override
        public OptionalLong getExchangeTimeMs(String orderId) {
            Order o = activeOrders.get(orderId);
            return o != null ? OptionalLong.of(o.exchangeTimeMs()) : OptionalLong.empty();
        }
    };

    // ── PortfolioProvider ─────────────────────────────────────────────

    private final PortfolioProvider portfolioProvider;

    // ── MarginProvider ────────────────────────────────────────────────

    private final MarginProvider marginProvider = request -> {
        long notional = request.quantity() * request.pricePaisa();
        long total = notional / 5;
        return new MarginEstimate(total, total / 2, total / 3, total / 20);
    };

    // ── InstrumentResolver ────────────────────────────────────────────

    private final InstrumentResolver instrumentResolver = new InstrumentResolver() {
        @Override
        public Instrument resolve(InstrumentKey key) { return toInstrument(key); }

        @Override
        public Instrument getBySymbol(InstrumentKey key) { return toInstrument(key); }

        @Override
        public Instrument resolveNormalized(String symbol, ExchangeSegment segment) {
            return toInstrument(new InstrumentKey(symbol, segment));
        }

        @Override
        public List<Instrument> allInstruments() { return List.of(); }

        @Override
        public Instrument resolveBySecurityId(String securityId) {
            return toInstrument(new InstrumentKey(securityId, ExchangeSegment.NSE_EQ));
        }

        @Override
        public Instrument requireDefinition(InstrumentKey key) { return toInstrument(key); }

        @Override
        public Instrument resolvePayload(Object payload) {
            throw new UnsupportedOperationException("Paper broker does not support payload resolution");
        }

        @Override
        public boolean isLoaded() { return true; }

        @Override
        public int catalogSize() { return basePricesPaisa.size(); }
    };

    // ── FuturesProvider ───────────────────────────────────────────────

    private final FuturesProvider futuresProvider = new FuturesProvider() {
        @Override
        public List<Instrument> getContracts(String underlying, ExchangeSegment segment) {
            return List.of(toInstrument(new InstrumentKey(underlying + "-FUT", segment)));
        }

        @Override
        public Instrument getNearestContract(String underlying, ExchangeSegment segment) {
            return toInstrument(new InstrumentKey(underlying + "-FUT", segment));
        }

        @Override
        public List<LocalDate> getExpiries(String underlying, ExchangeSegment segment) {
            LocalDate now = LocalDate.now();
            return List.of(now.withDayOfMonth(25).plusMonths(1));
        }

        @Override
        public boolean isCommodity(String underlying) { return false; }
    };

    // ── SliceOrderCommand ─────────────────────────────────────────────

    private final SliceOrderCommand sliceOrderCommand = request -> {
        String id = "SIM-SLICE-" + orderSeq.incrementAndGet();
        Order order = new Order(id, null, "SLICE", ExchangeSegment.NSE_EQ,
                com.tradej.core.domain.value.Side.BUY,
                com.tradej.core.domain.value.ProductType.INTRADAY,
                com.tradej.core.domain.value.OrderType.LIMIT,
                OrderStatus.TRADED, 1, 1, 0, 0,
                System.currentTimeMillis(), null);
        return List.of(order);
    };

    // ── WebSocketMultiplexer (no-op) ──────────────────────────────────

    private final WebSocketMultiplexer wsMultiplexer = new SimulatedWebSocketMultiplexer();

    // ── Helpers ───────────────────────────────────────────────────────

    private static Instrument toInstrument(InstrumentKey key) {
        Exchange exchange = key.exchangeSegment().exchange();
        return new Instrument(key.symbol(), key.symbol(), exchange, key.exchangeSegment(),
                "EQ", null, null, null, null, 1L, 5L);
    }
}
