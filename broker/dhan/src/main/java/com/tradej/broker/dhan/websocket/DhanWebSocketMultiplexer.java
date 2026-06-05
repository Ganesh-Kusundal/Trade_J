package com.tradej.broker.dhan.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.OrderUpdateListener;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.depth.DhanTwentyDepthWebSocketClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.mapper.DhanPayloadNormalizer;
import com.tradej.broker.core.resilience.BackoffStrategy;
import com.tradej.broker.dhan.websocket.feed.DhanMarketFeedPacket;
import com.tradej.core.domain.event.BrokerAdapterError;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.StreamHealthChanged;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;

import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DhanWebSocketMultiplexer implements WebSocketMultiplexer {
    private static final Logger log = LoggerFactory.getLogger(DhanWebSocketMultiplexer.class);
    private static final int INVALID_TOKEN_CODE = DhanProtocolConstants.INVALID_TOKEN_CODE;
    private static final long STALE_FEED_THRESHOLD_MS = DhanProtocolConstants.STALE_FEED_THRESHOLD_MS;
    private static final long TOKEN_CHECK_INTERVAL_MS = DhanProtocolConstants.TOKEN_CHECK_INTERVAL_MS;

    private final DhanClientHolder clientHolder;
    private final DhanInstrumentResolver resolver;
    private final DhanConnectionSettings settings;
    private final EventMetadataFactory metadataFactory;
    private final DhanPayloadNormalizer normalizer;
    private final Object transportLock = new Object();
    private final Map<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, OrderStatus> latestOrderStatuses = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<MarketDataListener> marketListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderUpdateListener> orderListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService healthMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "dhan-feed-health");
        thread.setDaemon(true);
        return thread;
    });

    private volatile DhanMarketFeedWebSocketClient marketFeedClient;
    private volatile DhanOrderStreamWebSocketClient orderStreamClient;
    private volatile boolean connected;
    private volatile boolean shutdown;
    private volatile long lastMarketEventAtMs;
    private volatile long lastTokenCheckAtMs;
    private volatile boolean staleFeedEmitted;
    private final ReconnectListenerRegistry reconnectRegistry;
    private final DhanTwentyDepthWebSocketClient depthClient;

    private int reconnectAttempts;
    private long circuitOpenUntilMs;

    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dhan-reconnect");
        t.setDaemon(true);
        return t;
    });

    public DhanWebSocketMultiplexer(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanConnectionSettings settings
    ) {
        this(clientHolder, resolver, settings, new EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock()), null, null);
    }

    public DhanWebSocketMultiplexer(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanConnectionSettings settings,
            EventMetadataFactory metadataFactory
    ) {
        this(clientHolder, resolver, settings, metadataFactory, null, null);
    }

    public DhanWebSocketMultiplexer(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanConnectionSettings settings,
            EventMetadataFactory metadataFactory,
            ReconnectListenerRegistry reconnectRegistry
    ) {
        this(clientHolder, resolver, settings, metadataFactory, reconnectRegistry, clientHolder.tokenProvider());
    }

    public DhanWebSocketMultiplexer(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanConnectionSettings settings,
            EventMetadataFactory metadataFactory,
            ReconnectListenerRegistry reconnectRegistry,
            DhanTokenProvider tokenProvider
    ) {
        this.clientHolder = clientHolder;
        this.resolver = resolver;
        this.settings = settings;
        this.metadataFactory = metadataFactory;
        this.normalizer = new DhanPayloadNormalizer(metadataFactory);
        this.reconnectRegistry = reconnectRegistry;
        DhanTokenProvider effectiveTokenProvider = tokenProvider == null ? clientHolder.tokenProvider() : tokenProvider;
        this.depthClient = settings.isSandbox()
                ? null
                : new DhanTwentyDepthWebSocketClient(settings, effectiveTokenProvider, resolver, metadataFactory);
        if (this.depthClient != null) {
            this.depthClient.onDepthUpdate(this::publishMarket);
        }
        this.clientHolder.addRotationListener(this::rebindAfterTokenRotation);
        healthMonitor.scheduleAtFixedRate(
                this::verifyFeedLiveness,
                DhanProtocolConstants.FEED_HEALTH_CHECK_INTERVAL_MS,
                DhanProtocolConstants.FEED_HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void connect() {
        shutdown = false;
        synchronized (transportLock) {
            ensureClientsLocked();
            resetLifecycleTimestampsLocked();
            resetReconnectCircuitLocked();
            connectMarketFeedLocked();
            connectOrderStreamLocked();
            connectDepthClientIfNeeded();
        }
    }

    @Override
    public void disconnect() {
        shutdown = true;
        connected = false;
        synchronized (transportLock) {
            closeClientsLocked();
        }
        if (depthClient != null) {
            depthClient.disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        return connected || (depthClient != null && depthClient.isConnected());
    }

    @Override
    public void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
        if (instruments.isEmpty()) {
            throw new IllegalArgumentException("Cannot subscribe an empty instrument set");
        }
        if (feedMode == FeedMode.DEPTH_20) {
            if (depthClient == null) {
                throw new IllegalStateException("Dhan 20-level depth feed requires a live token provider");
            }
            instruments.forEach(request -> subscriptions.put(request, feedMode));
            connectDepthClientIfNeeded();
            depthClient.subscribe(List.copyOf(instruments));
            lastMarketEventAtMs = System.currentTimeMillis();
            staleFeedEmitted = false;
            return;
        }
        List<DhanMarketFeedWebSocketClient.SubscriptionKey> keys = instruments.stream()
                .map(this::toFeedKey)
                .toList();
        synchronized (transportLock) {
            ensureClientsLocked();
            marketFeedClient.subscribe(keys, feedMode);
        }
        instruments.forEach(request -> subscriptions.put(request, feedMode));
        lastMarketEventAtMs = System.currentTimeMillis();
        staleFeedEmitted = false;
    }

    @Override
    public void unsubscribe(Collection<MarketSubscriptionRequest> instruments) {
        List<MarketSubscriptionRequest> depthRequests = instruments.stream()
                .filter(request -> subscriptions.get(request) == FeedMode.DEPTH_20)
                .toList();
        List<MarketSubscriptionRequest> marketRequests = instruments.stream()
                .filter(request -> subscriptions.get(request) != FeedMode.DEPTH_20)
                .toList();
        if (!marketRequests.isEmpty()) {
            synchronized (transportLock) {
                if (marketFeedClient != null) {
                    marketFeedClient.unsubscribe(marketRequests.stream().map(this::toFeedKey).toList());
                }
            }
        }
        if (!depthRequests.isEmpty() && depthClient != null) {
            depthClient.unsubscribe(depthRequests);
        }
        instruments.forEach(subscriptions::remove);
    }

    @Override
    public void onMarketData(MarketDataListener listener) {
        marketListeners.add(listener);
    }

    @Override
    public void onOrderUpdate(OrderUpdateListener listener) {
        orderListeners.add(listener);
    }

    @Override
    public Map<MarketSubscriptionRequest, FeedMode> subscriptions() {
        return Map.copyOf(subscriptions);
    }

    private void ensureClientsLocked() {
        if (marketFeedClient != null && orderStreamClient != null) {
            return;
        }
        bindClientsLocked();
    }

    private void bindClientsLocked() {
        DhanTokenProvider tokenProvider = clientHolder.tokenProvider();
        DhanMarketFeedWebSocketClient newMarketFeedClient = new DhanMarketFeedWebSocketClient(settings, tokenProvider);
        DhanOrderStreamWebSocketClient newOrderStreamClient = new DhanOrderStreamWebSocketClient(settings, tokenProvider);
        wireListeners(newMarketFeedClient, newOrderStreamClient);
        this.marketFeedClient = newMarketFeedClient;
        this.orderStreamClient = newOrderStreamClient;
    }

    private void closeClientsLocked() {
        if (marketFeedClient != null) {
            marketFeedClient.close();
            marketFeedClient = null;
        }
        if (orderStreamClient != null) {
            orderStreamClient.close();
            orderStreamClient = null;
        }
    }

    private void rebindAfterTokenRotation() {
        synchronized (transportLock) {
            if (shutdown) {
                return;
            }
            boolean shouldReconnect = connected || !subscriptions.isEmpty();
            closeClientsLocked();
            if (shouldReconnect) {
                bindClientsLocked();
                staleFeedEmitted = false;
                resetLifecycleTimestampsLocked();
                resetReconnectCircuitLocked();
                connectMarketFeedLocked();
                connectOrderStreamLocked();
            }
        }
        if (depthClient != null) {
            depthClient.disconnect();
            if (!shutdown && !subscriptions.isEmpty()) {
                connectDepthClientIfNeeded();
            }
        }
    }

    private void wireListeners(
            DhanMarketFeedWebSocketClient marketFeedClient,
            DhanOrderStreamWebSocketClient orderStreamClient
    ) {
        marketFeedClient.addListener(new DhanMarketFeedWebSocketClient.Listener() {
            @Override
            public void onConnected() {
                connected = true;
                lastMarketEventAtMs = System.currentTimeMillis();
                lastTokenCheckAtMs = lastMarketEventAtMs;
                staleFeedEmitted = false;
                scheduleResubscribe();
                publishMarket(healthEvent("dhan", "CONNECTED", 0));
            }

            @Override
            public void onDisconnected(int code, String reason) {
                connected = false;
                if (code == INVALID_TOKEN_CODE) {
                    publishMarket(brokerError("market-auth", "Dhan websocket token is invalid or expired"));
                }
                publishMarket(healthEvent("dhan", "DISCONNECTED", code));
            }

            @Override
            public void onError(Throwable error) {
                connected = false;
                publishMarket(brokerError("market-transport", error.getMessage()));
                publishMarket(healthEvent("dhan", "ERROR", 0));
            }

            @Override
            public void onPacket(DhanMarketFeedPacket packet, FeedMode feedMode) {
                handleFeedPacket(packet, feedMode);
            }
        });

        orderStreamClient.addListener(new DhanOrderStreamWebSocketClient.Listener() {
            @Override
            public void onConnected() {
                publishOrder(healthEvent("dhan-order", "CONNECTED", 0));
            }

            @Override
            public void onDisconnected(int code, String reason) {
                if (code == INVALID_TOKEN_CODE) {
                    publishOrder(brokerError("order-auth", "Dhan order stream token is invalid or expired"));
                }
                publishOrder(healthEvent("dhan-order", "DISCONNECTED", code));
            }

            @Override
            public void onError(Throwable error) {
                publishOrder(brokerError("order-transport", error.getMessage()));
                publishOrder(healthEvent("dhan-order", "ERROR", 0));
            }

            @Override
            public void onOrderUpdate(JsonNode update) {
                handleOrderPayload(update);
            }

            @Override
            public void onTradeUpdate(JsonNode update) {
                handleTradePayload(update);
            }
        });
    }

    private void handleFeedPacket(DhanMarketFeedPacket packet, FeedMode feedMode) {
        try {
            DhanInstrumentDefinition definition = resolver.requireSecurityId(packet.securityId());
            MarketTickEvent tick = normalizer.normalizeFeedPacket(packet, definition, feedMode);
            lastMarketEventAtMs = System.currentTimeMillis();
            staleFeedEmitted = false;
            publishMarket(tick);
        } catch (RuntimeException ex) {
            publishMarket(brokerError("market-normalization", ex.getMessage()));
        }
    }

    private void handleOrderPayload(JsonNode update) {
        try {
            DhanJsonResponse response = new DhanJsonResponse(update);
            DhanInstrumentDefinition definition = resolver.resolveDhanPayload(update);
            Order order = normalizer.normalizeOrder(response, definition);
            OrderStatus previousStatus = latestOrderStatuses.put(order.orderId(), order.status());
            if (order.status().isRejected() && previousStatus != OrderStatus.REJECTED) {
                publishOrder(new OrderRejected(metadataFactory.correlated(order.correlationId(), 0), order, order.rejectionReason()));
                return;
            }
            if (previousStatus == null) {
                publishOrder(new OrderAccepted(metadataFactory.correlated(order.correlationId(), 0), order));
            }
            if (order.status() == OrderStatus.PART_TRADED && previousStatus != OrderStatus.PART_TRADED) {
                publishOrder(new com.tradej.core.domain.event.OrderPartiallyFilled(
                        metadataFactory.correlated(order.correlationId(), 0), order, List.of()));
            } else if (order.status() == OrderStatus.TRADED && previousStatus != OrderStatus.TRADED) {
                publishOrder(new com.tradej.core.domain.event.OrderFullyFilled(
                        metadataFactory.correlated(order.correlationId(), 0), order, List.of()));
            }
        } catch (RuntimeException ex) {
            publishOrder(brokerError("order-normalization", ex.getMessage()));
        }
    }

    private void handleTradePayload(JsonNode update) {
        try {
            DhanJsonResponse response = new DhanJsonResponse(update);
            DhanInstrumentDefinition definition = resolver.resolveDhanPayload(update);
            Trade trade = normalizer.normalizeTrade(response, definition);
            Order order = normalizer.normalizeOrder(response, definition);
            latestOrderStatuses.put(order.orderId(), order.status());
            publishOrder(new OrderFilled(metadataFactory.correlated(order.correlationId(), 0), order, List.of(trade)));
        } catch (RuntimeException ex) {
            publishOrder(brokerError("trade-normalization", ex.getMessage()));
        }
    }

    private DhanMarketFeedWebSocketClient.SubscriptionKey toFeedKey(MarketSubscriptionRequest request) {
        DhanInstrumentDefinition definition = resolver.requireDhanDefinition(request.symbol(), request.exchangeSegment());
        return new DhanMarketFeedWebSocketClient.SubscriptionKey(definition.exchangeSegment(), definition.securityId());
    }

    private void resubscribeAll() {
        Map<FeedMode, List<MarketSubscriptionRequest>> grouped = subscriptions.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));
        grouped.forEach((feedMode, requests) -> subscribe(requests, feedMode));
    }

    private void connectDepthClientIfNeeded() {
        if (depthClient == null || shutdown) {
            return;
        }
        if (!subscriptions.containsValue(FeedMode.DEPTH_20)) {
            return;
        }
        if (!depthClient.isConnected()) {
            depthClient.connect();
            depthClient.resubscribeAll();
        }
    }

    private void scheduleResubscribe() {
        reconnectScheduler.schedule(() -> {
            synchronized (transportLock) {
                if (!shutdown && connected) {
                    resubscribeAll();
                }
            }
        }, 0, TimeUnit.MILLISECONDS);
    }

    private void verifyFeedLiveness() {
        if (shutdown || subscriptions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTokenCheckAtMs >= TOKEN_CHECK_INTERVAL_MS) {
            try {
                clientHolder.ensureValidToken();
            } catch (RuntimeException ex) {
                publishMarket(brokerError("token-refresh", ex.getMessage()));
            } finally {
                lastTokenCheckAtMs = now;
            }
        }
        if (!connected) {
            return;
        }
        long idleMs = now - lastMarketEventAtMs;
        if (idleMs >= STALE_FEED_THRESHOLD_MS) {
            if (!staleFeedEmitted) {
                staleFeedEmitted = true;
                publishMarket(brokerError("feed-heartbeat", "No market payload received for " + idleMs + "ms"));
                publishMarket(healthEvent("dhan", "STALE", (int) idleMs));
            }
            reconnectWithBackoff();
        }
    }

    private void reconnectWithBackoff() {
        long delayMs;
        synchronized (transportLock) {
            if (shutdown) {
                return;
            }
            long now = System.currentTimeMillis();
            if (circuitOpenUntilMs > now) {
                return;
            }
            reconnectAttempts++;
            if (reconnectAttempts >= DhanProtocolConstants.WS_RECONNECT_FAILURE_THRESHOLD) {
                circuitOpenUntilMs = now + DhanProtocolConstants.WS_RECONNECT_CIRCUIT_OPEN_MS;
                publishMarket(brokerError("reconnect-circuit",
                        "WebSocket reconnect circuit opened after " + reconnectAttempts + " consecutive failures"));
                publishMarket(healthEvent("dhan", "CIRCUIT_OPEN", reconnectAttempts));
                return;
            }
            delayMs = BackoffStrategy.computeDelayMs(
                    reconnectAttempts,
                    DhanProtocolConstants.WS_RECONNECT_BASE_DELAY_MS,
                    DhanProtocolConstants.WS_RECONNECT_MAX_DELAY_MS
            );
            closeClientsLocked();
            try {
                bindClientsLocked();
            } catch (RuntimeException ex) {
                publishMarket(brokerError("client-rebuild", ex.getMessage()));
                return;
            }
        }
        reconnectScheduler.schedule(this::executeReconnect, delayMs, TimeUnit.MILLISECONDS);
    }

    private void executeReconnect() {
        synchronized (transportLock) {
            if (shutdown) {
                return;
            }
            resetLifecycleTimestampsLocked();
            connectMarketFeedLocked();
            connectOrderStreamLocked();
        }
    }

    private void connectMarketFeedLocked() {
        try {
            marketFeedClient.connect();
        } catch (RuntimeException ex) {
            connected = false;
            publishMarket(brokerError("market-transport", ex.getMessage()));
            publishMarket(healthEvent("dhan", "ERROR", 0));
            throw ex;
        }
    }

    private void connectOrderStreamLocked() {
        try {
            orderStreamClient.connect();
        } catch (RuntimeException ex) {
            log.warn("Dhan order stream connect failed: {}", ex.getMessage());
            publishOrder(brokerError("order-transport", ex.getMessage()));
            publishOrder(healthEvent("dhan-order", "ERROR", 0));
        }
    }

    private void resetLifecycleTimestampsLocked() {
        connected = false;
        lastMarketEventAtMs = System.currentTimeMillis();
        lastTokenCheckAtMs = lastMarketEventAtMs;
    }

    private void resetReconnectCircuitLocked() {
        reconnectAttempts = 0;
        circuitOpenUntilMs = 0L;
    }

    private StreamHealthChanged healthEvent(String stream, String status, int detail) {
        return new StreamHealthChanged(metadataFactory.root(), stream, status, detail);
    }

    private BrokerAdapterError brokerError(String source, String message) {
        return new BrokerAdapterError(metadataFactory.root(), "dhan", source, message);
    }

    private void publishMarket(DomainEvent event) {
        if (event instanceof DepthUpdateEvent || event instanceof MarketTickEvent) {
            lastMarketEventAtMs = System.currentTimeMillis();
            staleFeedEmitted = false;
        }
        marketListeners.forEach(listener -> listener.onEvent(event));
    }

    private void publishOrder(DomainEvent event) {
        orderListeners.forEach(listener -> listener.onEvent(event));
    }
}
