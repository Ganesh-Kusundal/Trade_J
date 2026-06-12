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
import com.tradej.broker.dhan.mapper.DhanPayloadNormalizer;
import com.tradej.broker.dhan.websocket.feed.DhanMarketFeedPacket;
import com.tradej.core.domain.event.BrokerAdapterError;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.StreamHealthChanged;
import com.tradej.core.domain.value.FeedMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Facade that orchestrates Dhan WebSocket transport lifecycle, event normalization,
 * reconnection, and subscription management.
 *
 * <p>Delegates orthogonal responsibilities to specialised collaborators:
 * <ul>
 *   <li>{@link DhanWebSocketConnectionManager} — WebSocket client lifecycle</li>
 *   <li>{@link DhanMarketEventNormalizer} — packet/order/trade normalization &amp; dedup</li>
 *   <li>{@link DhanReconnectController} — reconnection state machine</li>
 *   <li>{@link DhanWebSocketSubscriptionManager} — subscription state &amp; depth client</li>
 *   <li>{@link DhanWebSocketHealthMonitor} — periodic token checks &amp; stale-feed detection</li>
 * </ul>
 */
public final class DhanWebSocketMultiplexer implements WebSocketMultiplexer {
    private static final Logger log = LoggerFactory.getLogger(DhanWebSocketMultiplexer.class);
    private static final int INVALID_TOKEN_CODE = DhanProtocolConstants.INVALID_TOKEN_CODE;

    // ---- Collaborators ----
    private final DhanClientHolder clientHolder;
    private final DhanWebSocketConnectionManager connectionManager;
    private final DhanMarketEventNormalizer eventNormalizer;
    private final DhanReconnectController reconnectController;
    private final DhanWebSocketSubscriptionManager subscriptionManager;
    private final DhanWebSocketHealthMonitor healthMonitor;
    private final EventMetadataFactory metadataFactory;

    // ---- Connection state (guarded by transportLock) ----
    private final Object transportLock = new Object();
    private volatile boolean shutdown;
    private boolean clientsWired;

    // ---- Event listeners ----
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
            com.tradej.broker.core.reconnect.ReconnectListenerRegistry reconnectRegistry
    ) {
        this(clientHolder, resolver, settings, metadataFactory, reconnectRegistry,
                clientHolder.tokenProvider());
    }

    public DhanWebSocketMultiplexer(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanConnectionSettings settings,
            EventMetadataFactory metadataFactory,
            com.tradej.broker.core.reconnect.ReconnectListenerRegistry reconnectRegistry,
            DhanTokenProvider tokenProvider
    ) {
        this.clientHolder = clientHolder;
        this.metadataFactory = metadataFactory;

        DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(metadataFactory);
        DhanTokenProvider effectiveTokenProvider = (tokenProvider != null)
                ? tokenProvider : clientHolder.tokenProvider();

        // ---- Create collaborators ----
        this.connectionManager = new DhanWebSocketConnectionManager(settings, effectiveTokenProvider);
        this.eventNormalizer = new DhanMarketEventNormalizer(resolver, normalizer, metadataFactory);
        this.reconnectController = new DhanReconnectController(this::publishMarket, metadataFactory, "dhan");
        this.subscriptionManager = new DhanWebSocketSubscriptionManager(resolver);

        // ---- Depth client setup ----
        if (!settings.isSandbox()) {
            DhanTwentyDepthWebSocketClient depthClient = new DhanTwentyDepthWebSocketClient(
                    settings, effectiveTokenProvider, resolver, metadataFactory);
            depthClient.onDepthUpdate(this::publishMarket);
            subscriptionManager.setDepthClient(depthClient);
        }

        // ---- Health monitor setup ----
        this.healthMonitor = new DhanWebSocketHealthMonitor(
                clientHolder,
                idleMs -> onHealthStaleFeed(idleMs),
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
            connectionManager.ensureClients();
            wireNewClients();
            healthMonitor.resetTimestamps();
            reconnectController.resetCircuit();
            connectionManager.connectMarketFeed();
            connectionManager.connectOrderStream();
            connectDepthClientIfNeeded();
        }
        healthMonitor.start();
        reconnectController.scheduleReconciliation(this::reconcileSubscriptions);
    }

    @Override
    public void disconnect() {
        shutdown = true;
        healthMonitor.stop();
        connectionManager.setConnected(false);
        synchronized (transportLock) {
            clientsWired = false;
            connectionManager.closeCurrentClients();
        }
        DhanTwentyDepthWebSocketClient depthClient = subscriptionManager.getDepthClient();
        if (depthClient != null) {
            depthClient.disconnect();
        }
        reconnectController.shutdown();
    }

    @Override
    public boolean isConnected() {
        return connectionManager.isConnected() || subscriptionManager.isDepthConnected();
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
            connectionManager.ensureClients();
            DhanMarketFeedWebSocketClient feedClient = connectionManager.marketFeedClient();
            if (feedClient != null) {
                feedClient.subscribe(keys, feedMode);
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
                DhanMarketFeedWebSocketClient feedClient = connectionManager.marketFeedClient();
                if (feedClient != null) {
                    feedClient.unsubscribe(subscriptionManager.toFeedKeys(marketRequests));
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

    // ---- Internal: connection ----

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

    private void onHealthStaleFeed(long idleMs) {
        synchronized (transportLock) {
            if (shutdown) {
                return;
            }
            reconnectController.startBackoff(
                    () -> {
                        connectionManager.closeCurrentClients();
                        connectionManager.bindClients();
                        clientsWired = false;
                        wireNewClients();
                    },
                    () -> {
                        healthMonitor.resetTimestamps();
                        connectionManager.setConnected(false);
                        connectionManager.connectMarketFeed();
                        connectionManager.connectOrderStream();
                        com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.recordReconnect("dhan");
                        com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.setActiveSubscriptions(
                                "dhan", subscriptionManager.snapshot().size());
                    }
            );
        }
    }

    // ---- Internal: token rotation ----

    private void rebindAfterTokenRotation() {
        synchronized (transportLock) {
            if (shutdown) {
                return;
            }
            boolean shouldReconnect = connectionManager.isConnected() || !subscriptionManager.isEmpty();
            connectionManager.closeCurrentClients();
            if (shouldReconnect) {
                connectionManager.bindClients();
                clientsWired = false;
                wireNewClients();
                healthMonitor.resetTimestamps();
                reconnectController.resetCircuit();
                connectionManager.connectMarketFeed();
                connectionManager.connectOrderStream();
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

    // ---- Internal: listener wiring ----

    /**
     * Wires listeners onto the current WebSocket clients.
     * Idempotent: listeners are attached at most once per client lifecycle.
     * Called after connect() and after every reconnect/token-rotation.
     */
    private void wireNewClients() {
        if (clientsWired) {
            return;
        }
        DhanMarketFeedWebSocketClient feedClient = connectionManager.marketFeedClient();
        DhanOrderStreamWebSocketClient orderClient = connectionManager.orderStreamClient();
        if (feedClient == null || orderClient == null) {
            return;
        }

        feedClient.addListener(new DhanMarketFeedWebSocketClient.Listener() {
            @Override
            public void onConnected() {
                connectionManager.setConnected(true);
                healthMonitor.resetTimestamps();
                eventNormalizer.resetDedup();
                com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.recordReconnect("dhan");
                com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.setActiveSubscriptions(
                        "dhan", subscriptionManager.snapshot().size());
                reconnectController.scheduleResubscribe(() -> {
                    synchronized (transportLock) {
                        if (!shutdown && connectionManager.isConnected()) {
                            resubscribeAll();
                        }
                    }
                });
                publishMarket(healthEvent("dhan", "CONNECTED", 0));
            }

            @Override
            public void onDisconnected(int code, String reason) {
                connectionManager.setConnected(false);
                if (code == INVALID_TOKEN_CODE) {
                    publishMarket(brokerError("market-auth",
                            "Dhan websocket token is invalid or expired"));
                    // CRITICAL-2 fix: force a fresh TOTP mint and propagate
                    // the rotation to the multiplexer via its rotation listener.
                    // The call is async so the disconnect callback returns
                    // immediately and the new token is bound on the next
                    // reconnect attempt.
                    long failedGen = clientHolder.tokenProvider().tokenGenerationId();
                    clientHolder.tokenProvider().invalidate(failedGen);
                    Thread.ofVirtual().name("dhan-rotate-market").start(() -> {
                        try {
                            clientHolder.ensureValidToken();
                        } catch (RuntimeException re) {
                            log.warn("Dhan market token rotation after invalid disconnect failed: {}",
                                    re.getMessage());
                        }
                    });
                }
                publishMarket(healthEvent("dhan", "DISCONNECTED", code));
            }

            @Override
            public void onError(Throwable error) {
                connectionManager.setConnected(false);
                publishMarket(brokerError("market-transport", error.getMessage()));
                publishMarket(healthEvent("dhan", "ERROR", 0));
            }

            @Override
            public void onPacket(DhanMarketFeedPacket packet, FeedMode feedMode) {
                handleFeedPacket(packet, feedMode);
            }
        });

        orderClient.addListener(new DhanOrderStreamWebSocketClient.Listener() {
            @Override
            public void onConnected() {
                publishOrder(healthEvent("dhan-order", "CONNECTED", 0));
            }

            @Override
            public void onDisconnected(int code, String reason) {
                if (code == INVALID_TOKEN_CODE) {
                    publishOrder(brokerError("order-auth",
                            "Dhan order stream token is invalid or expired"));
                    // Mirror the market feed's invalidation flow
                    // (CRITICAL-2 fix).
                    long failedGen = clientHolder.tokenProvider().tokenGenerationId();
                    clientHolder.tokenProvider().invalidate(failedGen);
                    Thread.ofVirtual().name("dhan-rotate-order").start(() -> {
                        try {
                            clientHolder.ensureValidToken();
                        } catch (RuntimeException re) {
                            log.warn("Dhan order-stream token rotation after invalid disconnect failed: {}",
                                    re.getMessage());
                        }
                    });
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
            java.util.Optional<MarketTickEvent> result = eventNormalizer.normalizeFeedPacket(packet, feedMode);
            if (result.isEmpty()) {
                return;
            }
            MarketTickEvent tick = result.get();
            healthMonitor.recordMarketEvent();
            com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.recordTickReceived("dhan");
            publishMarket(tick);
        } catch (RuntimeException ex) {
            com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.recordParseError("dhan");
            publishMarket(brokerError("market-normalization", ex.getMessage()));
        }
    }

    private void handleOrderPayload(JsonNode update) {
        try {
            Optional<DomainEvent> event = eventNormalizer.normalizeOrderPayload(update);
            event.ifPresent(this::publishOrder);
        } catch (RuntimeException ex) {
            publishOrder(brokerError("order-normalization", ex.getMessage()));
        }
    }

    private void handleTradePayload(JsonNode update) {
        try {
            var event = eventNormalizer.normalizeTradePayload(update);
            event.ifPresent(this::publishOrder);
        } catch (RuntimeException ex) {
            publishOrder(brokerError("trade-normalization", ex.getMessage()));
        }
    }

    // ---- Internal: resubscription ----

    private void resubscribeAll() {
        Map<FeedMode, List<MarketSubscriptionRequest>> grouped =
                subscriptionManager.groupedByMode();
        grouped.forEach((feedMode, requests) -> subscribe(requests, feedMode));
    }

    private void reconcileSubscriptions() {
        synchronized (transportLock) {
            if (!shutdown && connectionManager.isConnected() && !subscriptionManager.isEmpty()) {
                resubscribeAll();
            }
        }
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
