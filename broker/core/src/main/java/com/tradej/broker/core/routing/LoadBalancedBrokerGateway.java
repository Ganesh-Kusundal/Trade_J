package com.tradej.broker.core.routing;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.capability.BrokerCapabilityRouter;
import com.tradej.broker.api.model.HistoricalDataCapabilities;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.OrderUpdateListener;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.FeedMode;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Aggregates multiple {@link IBrokerConnection} nodes behind a single connection facade.
 *
 * <p>All routing sub-strategies live as nested static classes to keep the single-responsibility
 * boundary clear while eliminating the accidental spread across four separate top-level files.
 */
public final class LoadBalancedBrokerGateway implements IBrokerConnection {

    private final CopyOnWriteArrayList<IBrokerConnection> connections;
    private final AtomicInteger primaryIndex = new AtomicInteger();
    private final LoadBalancedMarketDataProvider marketData;
    private final FailoverOrderCommand orders;
    private final FailoverWebSocketMultiplexer websocket;

    public LoadBalancedBrokerGateway(List<IBrokerConnection> connections) {
        this(connections, null);
    }

    public LoadBalancedBrokerGateway(List<IBrokerConnection> connections, ReconnectListenerRegistry reconnectRegistry) {
        if (connections == null || connections.isEmpty()) {
            throw new IllegalArgumentException("At least one broker connection is required");
        }
        this.connections = new CopyOnWriteArrayList<>(connections);
        List<MarketDataProvider> marketDataProviders = connections.stream()
                .map(IBrokerConnection::marketData)
                .toList();
        this.marketData = new LoadBalancedMarketDataProvider(marketDataProviders);
        this.orders = new FailoverOrderCommand(connections, this::rotatePrimary);
        this.websocket = new FailoverWebSocketMultiplexer(connections, reconnectRegistry);
    }

    public void addConnection(IBrokerConnection connection) {
        connections.addIfAbsent(connection);
    }

    public void removeConnection(IBrokerConnection connection) {
        connections.remove(connection);
    }

    public void rotatePrimary() {
        primaryIndex.incrementAndGet();
    }

    public int connectionCount() {
        return connections.size();
    }

    private IBrokerConnection primary() {
        return connections.get(Math.floorMod(primaryIndex.get(), connections.size()));
    }

    @Override public MarketDataProvider marketData()         { return marketData; }
    @Override public FuturesProvider   futures()             { return primary().futures(); }
    @Override public OrderCommand      orders()              { return orders; }
    @Override public OrderQuery        orderQuery()          { return primary().orderQuery(); }
    @Override public SliceOrderCommand sliceOrders()         { return primary().sliceOrders(); }
    @Override public BracketOrderProvider bracketOrders()    { return primary().bracketOrders(); }
    @Override public GttOrderProvider  gttOrders()           { return primary().gttOrders(); }
    @Override public PortfolioProvider portfolio()           { return primary().portfolio(); }
    @Override public MarginProvider    margin()              { return primary().margin(); }
    @Override public SessionRiskProvider sessionRisk()       { return primary().sessionRisk(); }
    @Override public ConditionalAlertProvider alerts()       { return primary().alerts(); }
    @Override public InstrumentResolver instruments()        { return primary().instruments(); }
    @Override public WebSocketMultiplexer websocket()        { return websocket; }

    @Override
    public OptionsProvider options() {
        for (IBrokerConnection connection : connections) {
            if (BrokerCapabilityRouter.forConnection(connection).supports(OptionsProvider.class)) {
                return connection.options();
            }
        }
        return primary().options();
    }

    @Override
    public NewsProvider news() {
        for (IBrokerConnection connection : connections) {
            if (BrokerCapabilityRouter.forConnection(connection).supports(NewsProvider.class)) {
                return connection.news();
            }
        }
        return primary().news();
    }

    @Override
    public void connect() {
        for (IBrokerConnection connection : connections) { connection.connect(); }
    }

    @Override
    public void disconnect() {
        for (IBrokerConnection connection : connections) { connection.disconnect(); }
    }

    @Override
    public void loadInstrumentCatalog(Path catalogPath) {
        for (IBrokerConnection connection : connections) { connection.loadInstrumentCatalog(catalogPath); }
    }

    @Override
    public <T> Optional<T> getCapability(Class<T> capabilityClass) {
        if (capabilityClass == null) return Optional.empty();
        if (capabilityClass.isAssignableFrom(getClass())) {
            return Optional.of(capabilityClass.cast(this));
        }
        for (IBrokerConnection connection : connections) {
            Optional<T> capability = BrokerCapabilityRouter.forConnection(connection).find(capabilityClass);
            if (capability.isPresent()) return capability;
        }
        return Optional.empty();
    }

    public List<IBrokerConnection> connections() {
        return List.copyOf(connections);
    }

    // ── Nested routing strategies ──────────────────────────────────────────────

    /**
     * Round-robin LTP lookups across broker nodes; failover for heavier REST calls.
     */
    static final class LoadBalancedMarketDataProvider implements MarketDataProvider {

        private final List<MarketDataProvider> providers;
        private final AtomicInteger ltpIndex = new AtomicInteger();

        LoadBalancedMarketDataProvider(List<MarketDataProvider> providers) {
            if (providers == null || providers.isEmpty()) {
                throw new IllegalArgumentException("At least one market data provider is required");
            }
            this.providers = List.copyOf(providers);
        }

        @Override
        public long getLtpPaisa(InstrumentKey instrumentKey) {
            int start = Math.floorMod(ltpIndex.getAndIncrement(), providers.size());
            RuntimeException last = null;
            for (int i = 0; i < providers.size(); i++) {
                MarketDataProvider provider = providers.get(Math.floorMod(start + i, providers.size()));
                try {
                    return provider.getLtpPaisa(instrumentKey);
                } catch (RuntimeException ex) {
                    last = ex;
                }
            }
            throw new IllegalStateException("All broker market-data nodes failed for " + instrumentKey, last);
        }

        @Override
        public Quote getQuote(InstrumentKey instrumentKey) {
            return withFailover(p -> p.getQuote(instrumentKey));
        }

        @Override
        public MarketDepth getDepth(InstrumentKey instrumentKey) {
            return withFailover(p -> p.getDepth(instrumentKey));
        }

        @Override
        public Quote getOhlcSnapshot(InstrumentKey instrumentKey) {
            return withFailover(p -> p.getOhlcSnapshot(instrumentKey));
        }

        @Override
        public List<Candle> getCandles(CandleHistoryRequest request) {
            return withFailover(p -> p.getCandles(request));
        }

        @Override
        public HistoricalDataCapabilities capabilities() {
            return providers.isEmpty() ? null : providers.getFirst().capabilities();
        }

        @Override
        public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> instrumentKeys) {
            return withFailover(p -> p.getLtpBatch(instrumentKeys));
        }

        @Override
        public Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> instrumentKeys) {
            return withFailover(p -> p.getQuoteBatch(instrumentKeys));
        }

        @Override
        public Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> instrumentKeys) {
            return withFailover(p -> p.getOhlcBatch(instrumentKeys));
        }

        private <T> T withFailover(Function<MarketDataProvider, T> call) {
            RuntimeException last = null;
            for (MarketDataProvider provider : providers) {
                try {
                    return call.apply(provider);
                } catch (RuntimeException ex) {
                    last = ex;
                }
            }
            throw new IllegalStateException("All broker market-data nodes failed", last);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Places and manages orders with round-robin primary selection and failover on errors.
     */
    static final class FailoverOrderCommand implements OrderCommand {

        private final List<IBrokerConnection> connections;
        private final AtomicInteger primaryIndex = new AtomicInteger();
        private final Runnable onRotate;

        FailoverOrderCommand(List<IBrokerConnection> connections) {
            this(connections, () -> {});
        }

        FailoverOrderCommand(List<IBrokerConnection> connections, Runnable onRotate) {
            if (connections == null || connections.isEmpty()) {
                throw new IllegalArgumentException("At least one broker connection is required");
            }
            this.connections = List.copyOf(connections);
            this.onRotate = Objects.requireNonNullElse(onRotate, () -> {});
        }

        @Override
        public Order placeOrder(OrderRequest request) {
            return withFailover(c -> c.orders().placeOrder(request));
        }

        @Override
        public Order modifyOrder(ModifyOrderRequest request) {
            return withFailover(c -> c.orders().modifyOrder(request));
        }

        @Override
        public boolean cancelOrder(String orderId) {
            return withFailover(c -> c.orders().cancelOrder(orderId));
        }

        @Override
        public List<String> cancelAllOpenOrders() {
            return withFailover(c -> c.orders().cancelAllOpenOrders());
        }

        @Override
        public List<String> cancelAndSquareOffIntradayPositions() {
            return withFailover(c -> c.orders().cancelAndSquareOffIntradayPositions());
        }

        @Override
        public boolean setKillSwitch(boolean enabled) {
            boolean result = false;
            for (IBrokerConnection connection : connections) {
                result |= connection.orders().setKillSwitch(enabled);
            }
            return result;
        }

        @Override
        public OrderPreview previewOrder(OrderRequest request) {
            return connections.get(Math.floorMod(primaryIndex.get(), connections.size()))
                    .orders().previewOrder(request);
        }

        private <T> T withFailover(Function<IBrokerConnection, T> action) {
            int start = Math.floorMod(primaryIndex.get(), connections.size());
            RuntimeException last = null;
            for (int i = 0; i < connections.size(); i++) {
                IBrokerConnection connection = connections.get(Math.floorMod(start + i, connections.size()));
                try {
                    return action.apply(connection);
                } catch (RuntimeException ex) {
                    last = ex;
                    primaryIndex.incrementAndGet();
                    onRotate.run();
                }
            }
            throw new IllegalStateException("All broker order nodes failed", last);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Fans out websocket subscriptions and multiplexes inbound events from all broker nodes.
     */
    static final class FailoverWebSocketMultiplexer implements WebSocketMultiplexer {

        private final List<IBrokerConnection> connections;
        private final CopyOnWriteArrayList<MarketDataListener> marketDataListeners = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<OrderUpdateListener> orderUpdateListeners = new CopyOnWriteArrayList<>();
        private final ReconnectListenerRegistry reconnectRegistry;

        FailoverWebSocketMultiplexer(List<IBrokerConnection> connections) {
            this(connections, null);
        }

        FailoverWebSocketMultiplexer(List<IBrokerConnection> connections, ReconnectListenerRegistry reconnectRegistry) {
            if (connections == null || connections.isEmpty()) {
                throw new IllegalArgumentException("At least one broker connection is required");
            }
            this.connections = List.copyOf(connections);
            this.reconnectRegistry = reconnectRegistry;
            for (IBrokerConnection connection : this.connections) {
                connection.websocket().onMarketData(event -> {
                    for (MarketDataListener listener : marketDataListeners) { listener.onEvent(event); }
                });
                connection.websocket().onOrderUpdate(event -> {
                    for (OrderUpdateListener listener : orderUpdateListeners) { listener.onEvent(event); }
                });
            }
        }

        @Override
        public void connect() {
            for (IBrokerConnection connection : connections) { connection.websocket().connect(); }
        }

        @Override
        public void disconnect() {
            for (IBrokerConnection connection : connections) { connection.websocket().disconnect(); }
        }

        @Override
        public boolean isConnected() {
            return connections.stream().anyMatch(c -> c.websocket().isConnected());
        }

        @Override
        public void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
            for (IBrokerConnection connection : connections) {
                connection.websocket().subscribe(instruments, feedMode);
            }
        }

        @Override
        public void unsubscribe(Collection<MarketSubscriptionRequest> instruments) {
            for (IBrokerConnection connection : connections) {
                connection.websocket().unsubscribe(instruments);
            }
        }

        @Override
        public void onMarketData(MarketDataListener listener) {
            marketDataListeners.add(Objects.requireNonNull(listener));
        }

        @Override
        public void onOrderUpdate(OrderUpdateListener listener) {
            orderUpdateListeners.add(Objects.requireNonNull(listener));
        }

        @Override
        public Map<MarketSubscriptionRequest, FeedMode> subscriptions() {
            Map<MarketSubscriptionRequest, FeedMode> merged = new ConcurrentHashMap<>();
            for (IBrokerConnection connection : connections) {
                merged.putAll(connection.websocket().subscriptions());
            }
            return Map.copyOf(merged);
        }

        /** Registers a listener that is notified when any downstream broker reconnects. */
        public void onReconnect(Runnable listener) {
            if (reconnectRegistry != null) {
                reconnectRegistry.addListener(listener);
            }
        }
    }
}
