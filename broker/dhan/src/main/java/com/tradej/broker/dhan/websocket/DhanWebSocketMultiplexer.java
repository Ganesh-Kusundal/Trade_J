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
import com.tradej.broker.dhan.websocket.feed.DhanMarketFeedPacket;
import com.tradej.broker.core.resilience.BackoffStrategy;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Facade that manages Dhan WebSocket transport lifecycle, event wiring, and reconnection.
 *
 * <p>This class owns the connection lifecycle (market feed + order stream WebSocket clients)
 * and delegates orthogonal responsibilities to specialised collaborators:
 * <ul>
 *   <li>{@link DhanWebSocketSubscriptionManager} — subscription state and depth client</li>
 *   <li>{@link DhanWebSocketHealthMonitor} — periodic token checks and stale-feed detection</li>
 * </ul>
 */
public final class DhanWebSocketMultiplexer implements WebSocketMultiplexer {
    private static final Logger log = LoggerFactory.getLogger(DhanWebSocketMultiplexer.class);
    private static final int INVALID_TOKEN_CODE = DhanProtocolConstants.INVALID_TOKEN_CODE;

    // ---- Injected dependencies ----
    private final DhanClientHolder clientHolder;
    private final DhanInstrumentResolver resolver;
    private final DhanConnectionSettings settings;
    private final EventMetadataFactory metadataFactory;
    private final DhanPayloadNormalizer normalizer;
    private final DhanWebSocketSubscriptionManager subscriptionManager;
    private final DhanWebSocketHealthMonitor healthMonitor;
    private final ReconnectListenerRegistry reconnectRegistry;

    // ---- Connection state (guarded by transportLock) ----
    private final Object transportLock = new Object();
    private DhanMarketFeedWebSocketClient marketFeedClient;
    private DhanOrderStreamWebSocketClient orderStreamClient;
    private volatile boolean connected;
    private volatile boolean shutdown;

    // ---- Reconnection state ----
    private int reconnectAttempts;
    private long circuitOpenUntilMs;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dhan-reconnect");
        t.setDaemon(true);
        return t;
    });

    // ---- Event listeners ----
    private final Map<String, OrderStatus> latestOrderStatuses = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<MarketDataListener> marketListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderUpdateListener> orderListeners = new CopyOnWriteArrayList<>();

    // ---- Constructors ----

    public DhanWebSocketMultiplexer(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanConnectionSettings settings
    ) {
        this(clientHolder, resolver, settings, new EventMetadataFactory(
                new com.tradej.core.domain.time.LiveTradingClock()), null, null);
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
        this(clientHolder, resolver, settings, metadataFactory, reconnectRegistry,
                clientHolder.tokenProvider());
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
        this.subscriptionManager = new DhanWebSocketSubscriptionManager(resolver);

        // ---- Depth client setup ----
        DhanTokenProvider effectiveTokenProvider = (tokenProvider != null)
                ? tokenProvider : clientHolder.tokenProvider();
        if (!settings.isSandbox()) {
            DhanTwentyDepthWebSocketClient depthClient = new DhanTwentyDepthWebSocketClient(
                    settings, effectiveTokenProvider, resolver, metadataFactory);
            depthClient.onDepthUpdate(this::publishMarket);
            subscriptionManager.setDepthClient(depthClient);
        }

        // ---- Health monitor setup ----
        this.healthMonitor = new DhanWebSocketHealthMonitor(
                clientHolder,
                idleMs -> reconnectWithBackoff(),
                this::publishMarket,
                metadataFactory,
                "dhan"
        );

        // ---- Token rotation callback ----
        this.clientHolder.addRotationListener(this::rebindAfterTokenRotation);
    }

    // ---- WebSocketMultiplexer interface ----

    @Override
    public void connect() {
        shutdown = false;
        synchronized (transportLock) {
            ensureClientsLocked();
            healthMonitor.resetTimestamps();
            resetReconnectCircuitLocked();
            connectMarketFeedLocked();
            connectOrderStreamLocked();
            connectDepthClientIfNeeded();
        }
        healthMonitor.start();
    }

    @Override
    public void disconnect() {
        shutdown = true;
        healthMonitor.stop();
        connected = false;
        synchronized (transportLock) {
            closeClientsLocked();
        }
        DhanTwentyDepthWebSocketClient depthClient = subscriptionManager.getDepthClient();
        if (depthClient != null) {
            depthClient.disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        return connected || subscriptionManager.isDepthConnected();
    }

    @Override
    public void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
        if (instruments.isEmpty()) {
            throw new IllegalArgumentException("Cannot subscribe an empty instrument set");
        }

        if (feedMode == FeedMode.DEPTH_20) {
            DhanTwentyDepthWebSocketClient depthClient = subscriptionManager.getDepthClient();
            if (depthClient == null) {
                throw new IllegalStateException(
                        "Dhan 20-level depth feed requires a live token provider");
            }
            subscriptionManager.addAll(instruments, feedMode);
            connectDepthClientIfNeeded();
            depthClient.subscribe(List.copyOf(instruments));
            healthMonitor.recordMarketEvent();
            return;
        }

        // Regular market feed subscription
        List<DhanMarketFeedWebSocketClient.SubscriptionKey> keys =
                subscriptionManager.toFeedKeys(instruments);
        synchronized (transportLock) {
            ensureClientsLocked();
            if (marketFeedClient != null) {
                marketFeedClient.subscribe(keys, feedMode);
            }
        }
        subscriptionManager.addAll(instruments, feedMode);
        healthMonitor.recordMarketEvent();
    }

    @Override
    public void unsubscribe(Collection<MarketSubscriptionRequest> instruments) {
        List<MarketSubscriptionRequest> depthRequests = new ArrayList<>();
        List<MarketSubscriptionRequest> marketRequests = new ArrayList<>();
        subscriptionManager.partitionByDepth(instruments, depthRequests, marketRequests);

        if (!marketRequests.isEmpty()) {
            synchronized (transportLock) {
                if (marketFeedClient != null) {
                    marketFeedClient.unsubscribe(
                            subscriptionManager.toFeedKeys(marketRequests));
                }
            }
        }

        DhanTwentyDepthWebSocketClient depthClient = subscriptionManager.getDepthClient();
        if (!depthRequests.isEmpty() && depthClient != null) {
            depthClient.unsubscribe(depthRequests);
        }

        subscriptionManager.removeAll(instruments);
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
        return subscriptionManager.snapshot();
    }

    // ---- Internal: client lifecycle (all locked) ----

    private void ensureClientsLocked() {
        if (marketFeedClient != null && orderStreamClient != null) {
            return;
        }
        bindClientsLocked();
    }

    private void bindClientsLocked() {
        DhanTokenProvider tokenProvider = clientHolder.tokenProvider();
        DhanMarketFeedWebSocketClient newMarketFeedClient =
                new DhanMarketFeedWebSocketClient(settings, tokenProvider);
        DhanOrderStreamWebSocketClient newOrderStreamClient =
                new DhanOrderStreamWebSocketClient(settings, tokenProvider);
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

    // ---- Internal: connection ----

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

    private void connectDepthClientIfNeeded() {
        DhanTwentyDepthWebSocketClient depthClient = subscriptionManager.getDepthClient();
        if (depthClient == null || shutdown) {
            return;
        }
        if (!subscriptionManager.hasDepthSubscriptions()) {
            return;
        }
        if (!depthClient.isConnected()) {
            depthClient.connect();
            depthClient.resubscribeAll();
        }
    }

    // ---- Internal: reconnection ----

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
                        "WebSocket reconnect circuit opened after "
                                + reconnectAttempts + " consecutive failures"));
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
            healthMonitor.resetTimestamps();
            connected = false;
            connectMarketFeedLocked();
            connectOrderStreamLocked();
            if (reconnectRegistry != null) {
                reconnectRegistry.notifyReconnect();
            }
        }
    }

    // ---- Internal: token rotation ----

    private void rebindAfterTokenRotation() {
        synchronized (transportLock) {
            if (shutdown) {
                return;
            }
            boolean shouldReconnect = connected || !subscriptionManager.isEmpty();
            closeClientsLocked();
            if (shouldReconnect) {
            bindClientsLocked();
            healthMonitor.resetTimestamps();
            resetReconnectCircuitLocked();
                connectMarketFeedLocked();
                connectOrderStreamLocked();
            }
        }
        DhanTwentyDepthWebSocketClient depthClient = subscriptionManager.getDepthClient();
        if (depthClient != null) {
            depthClient.disconnect();
            if (!shutdown && !subscriptionManager.isEmpty()) {
                connectDepthClientIfNeeded();
            }
        }
    }

    private void resetReconnectCircuitLocked() {
        reconnectAttempts = 0;
        circuitOpenUntilMs = 0L;
    }

    // ---- Internal: listener wiring ----

    private void wireListeners(
            DhanMarketFeedWebSocketClient marketFeedClient,
            DhanOrderStreamWebSocketClient orderStreamClient
    ) {
        marketFeedClient.addListener(new DhanMarketFeedWebSocketClient.Listener() {
            @Override
            public void onConnected() {
                connected = true;
                healthMonitor.resetTimestamps();
                scheduleResubscribe();
                publishMarket(healthEvent("dhan", "CONNECTED", 0));
            }

            @Override
            public void onDisconnected(int code, String reason) {
                connected = false;
                if (code == INVALID_TOKEN_CODE) {
                    publishMarket(brokerError("market-auth",
                            "Dhan websocket token is invalid or expired"));
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
                    publishOrder(brokerError("order-auth",
                            "Dhan order stream token is invalid or expired"));
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

    // ---- Internal: packet handling ----

    private void handleFeedPacket(DhanMarketFeedPacket packet, FeedMode feedMode) {
        try {
            DhanInstrumentDefinition definition =
                    resolver.requireSecurityId(packet.securityId());
            MarketTickEvent tick = normalizer.normalizeFeedPacket(packet, definition, feedMode);
            healthMonitor.recordMarketEvent();
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
                publishOrder(new OrderRejected(
                        metadataFactory.correlated(order.correlationId(), 0),
                        order, order.rejectionReason()));
                return;
            }
            if (previousStatus == null) {
                publishOrder(new OrderAccepted(
                        metadataFactory.correlated(order.correlationId(), 0), order));
            }
            // PART_TRADED/TRADED status transitions are handled by handleTradePayload
            // which emits OrderFilled with actual trade data — suppress empty-fill status
            // events to avoid duplicate processing in ExecutionHandler.
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
            publishOrder(new OrderFilled(
                    metadataFactory.correlated(order.correlationId(), 0),
                    order, List.of(trade)));
        } catch (RuntimeException ex) {
            publishOrder(brokerError("trade-normalization", ex.getMessage()));
        }
    }

    // ---- Internal: resubscription ----

    private void scheduleResubscribe() {
        reconnectScheduler.schedule(() -> {
            synchronized (transportLock) {
                if (!shutdown && connected) {
                    resubscribeAll();
                }
            }
        }, 0, TimeUnit.MILLISECONDS);
    }

    private void resubscribeAll() {
        Map<FeedMode, List<MarketSubscriptionRequest>> grouped =
                subscriptionManager.groupedByMode();
        grouped.forEach((feedMode, requests) -> subscribe(requests, feedMode));
    }

    // ---- Internal: event helpers ----

    private StreamHealthChanged healthEvent(String stream, String status, int detail) {
        return new StreamHealthChanged(metadataFactory.root(), stream, status, detail);
    }

    private BrokerAdapterError brokerError(String source, String message) {
        return new BrokerAdapterError(metadataFactory.root(), "dhan", source, message);
    }

    private void publishMarket(DomainEvent event) {
        if (event instanceof DepthUpdateEvent || event instanceof MarketTickEvent) {
            healthMonitor.recordMarketEvent();
        }
        marketListeners.forEach(listener -> listener.onEvent(event));
    }

    private void publishOrder(DomainEvent event) {
        orderListeners.forEach(listener -> listener.onEvent(event));
    }
}
