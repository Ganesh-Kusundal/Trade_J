package com.tradej.broker.upstox.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.OrderUpdateListener;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.broker.core.reconnect.ReconnectManager;
import com.tradej.broker.core.websocket.DefaultWebSocketSupervisor;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderCancelled;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderFullyFilled;
import com.tradej.core.domain.event.OrderModified;
import com.tradej.core.domain.event.OrderPartiallyFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.OrderUpdateEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class UpstoxWebSocketMultiplexer implements WebSocketMultiplexer {

    private static final long STALE_THRESHOLD_MS = 30_000L;
    private static final long HEALTH_CHECK_INTERVAL_MS = 5_000L;
    private static final int MAX_RECONNECT_ATTEMPTS = 8;
    private static final long RECONNECT_BASE_DELAY_MS = 1_000L;
    private static final long RECONNECT_MAX_DELAY_MS = 60_000L;
    private static final long RECONNECT_JITTER_MS = 500L;

    private final UpstoxFeedAuthorizer feedAuthorizer;
    private final UpstoxStreamNormalizer streamNormalizer;
    private final UpstoxPortfolioStreamParser portfolioStreamParser;
    private final UpstoxInstrumentResolver instrumentResolver;
    private final EventMetadataFactory metadataFactory;
    private final DefaultWebSocketSupervisor supervisor;
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private final ReconnectListenerRegistry reconnectRegistry;
    private final ReconnectManager reconnectManager;
    private final HttpClient httpClient;
    private final AtomicBoolean reconnectInProgress = new AtomicBoolean(false);
    private final AtomicBoolean manuallyDisconnected = new AtomicBoolean(false);

    private final ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<MarketDataListener> marketDataListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderUpdateListener> orderUpdateListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, OrderStatus> latestOrderStatuses = new ConcurrentHashMap<>();

    private final ScheduledExecutorService healthExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "upstox-feed-health"));
    private final StringBuilder orderMessageBuffer = new StringBuilder();

    private volatile java.net.http.WebSocket marketWs;
    private volatile java.net.http.WebSocket orderWs;
    private volatile boolean connected;
    private volatile String marketWsUri = "";
    private volatile String orderWsUri = "";
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    public UpstoxWebSocketMultiplexer(
            UpstoxFeedAuthorizer feedAuthorizer,
            UpstoxStreamNormalizer streamNormalizer,
            UpstoxInstrumentResolver instrumentResolver,
            EventMetadataFactory metadataFactory
    ) {
        this(feedAuthorizer, streamNormalizer, new UpstoxPortfolioStreamParser(metadataFactory),
                instrumentResolver, metadataFactory, null);
    }

    public UpstoxWebSocketMultiplexer(
            UpstoxFeedAuthorizer feedAuthorizer,
            UpstoxStreamNormalizer streamNormalizer,
            UpstoxInstrumentResolver instrumentResolver,
            EventMetadataFactory metadataFactory,
            ReconnectListenerRegistry reconnectRegistry
    ) {
        this(feedAuthorizer, streamNormalizer, new UpstoxPortfolioStreamParser(metadataFactory),
                instrumentResolver, metadataFactory, reconnectRegistry);
    }

    public UpstoxWebSocketMultiplexer(
            UpstoxFeedAuthorizer feedAuthorizer,
            UpstoxStreamNormalizer streamNormalizer,
            UpstoxPortfolioStreamParser portfolioStreamParser,
            UpstoxInstrumentResolver instrumentResolver,
            EventMetadataFactory metadataFactory,
            ReconnectListenerRegistry reconnectRegistry
    ) {
        this.feedAuthorizer = feedAuthorizer;
        this.streamNormalizer = streamNormalizer;
        this.portfolioStreamParser = portfolioStreamParser;
        this.instrumentResolver = instrumentResolver;
        this.metadataFactory = metadataFactory;
        this.reconnectRegistry = reconnectRegistry;
        this.supervisor = new DefaultWebSocketSupervisor(STALE_THRESHOLD_MS);
        this.reconnectManager = new ReconnectManager(
                MAX_RECONNECT_ATTEMPTS, RECONNECT_BASE_DELAY_MS, RECONNECT_MAX_DELAY_MS);
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void connect() {
        manuallyDisconnected.set(false);
        try {
            // 1. Connect market data WebSocket
            var marketAuth = feedAuthorizer.authorize();
            marketWsUri = marketAuth.wsUri();
            marketWs = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(marketWsUri), new MarketFeedHandler())
                    .join();
            connected = true;
            connectionState = ConnectionState.CONNECTED;
            supervisor.onConnected();

            // 2. Connect order/portfolio stream WebSocket (sequential, best-effort)
            connectOrderWebSocket();

            // 3. Start health checks
            healthExecutor.scheduleAtFixedRate(this::checkHealth,
                    HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            connectionState = ConnectionState.FAILED;
            // If market WS failed, propagate; if only order WS failed, still consider connected
            if (marketWs == null) {
                throw new RuntimeException("Failed to connect Upstox WebSocket", e);
            }
        }
    }

    private void connectOrderWebSocket() {
        try {
            var orderAuth = feedAuthorizer.authorizePortfolioStream();
            orderWsUri = orderAuth.wsUri();
            orderWs = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(orderWsUri), new OrderFeedHandler())
                    .join();
        } catch (Exception e) {
            // Best-effort: if order WS fails, log and continue with market data only
            orderWsUri = "";
            orderWs = null;
        }
    }

    @Override
    public void disconnect() {
        manuallyDisconnected.set(true);
        connected = false;
        connectionState = ConnectionState.DISCONNECTED;
        if (marketWs != null) {
            marketWs.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "");
            marketWs = null;
        }
        if (orderWs != null) {
            orderWs.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "");
            orderWs = null;
        }
        healthExecutor.shutdown();
        supervisor.disconnect();
        reconnectManager.reset();
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public boolean isManuallyDisconnected() {
        return manuallyDisconnected.get();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
        for (var req : instruments) {
            subscriptions.put(req, feedMode);
        }
    }

    @Override
    public void unsubscribe(Collection<MarketSubscriptionRequest> instruments) {
        instruments.forEach(subscriptions::remove);
    }

    @Override
    public void onMarketData(MarketDataListener listener) {
        marketDataListeners.add(listener);
    }

    @Override
    public void onOrderUpdate(OrderUpdateListener listener) {
        orderUpdateListeners.add(listener);
    }

    @Override
    public Map<MarketSubscriptionRequest, FeedMode> subscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    private void handleBinaryFrame(ByteBuffer buffer) {
        try {
            ParsedFeedFrame frame = UpstoxBinaryParser.parse(buffer);
            if (frame == null) {
                return;
            }
            supervisor.onMessage(buffer);
            long sequenceId = sequenceCounter.incrementAndGet();
            MarketSubscriptionRequest key = findKey(frame.instrumentToken());
            FeedMode feedMode = key != null ? subscriptions.getOrDefault(key, FeedMode.TICKER) : FeedMode.TICKER;
            MarketTickEvent event = streamNormalizer.toMarketTick(frame, feedMode, sequenceId);
            for (var listener : marketDataListeners) {
                listener.onEvent(event);
            }
        } catch (UpstoxBinaryParser.UpstoxParserException e) {
            // malformed frame — skip
        }
    }

    private MarketSubscriptionRequest findKey(long instrumentToken) {
        String symbol = instrumentResolver.resolveSymbol(instrumentToken);
        if (symbol == null) return null;
        return subscriptions.keySet().stream()
                .filter(k -> k.symbol().equals(symbol))
                .findFirst()
                .orElse(null);
    }

    private void checkHealth() {
        supervisor.checkStaleness();
        if (supervisor.state() == DefaultWebSocketSupervisor.State.STALE) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (manuallyDisconnected.get()) {
            return; // Don't reconnect if user explicitly disconnected
        }
        if (!reconnectInProgress.compareAndSet(false, true)) {
            return;
        }
        healthExecutor.execute(() -> {
            try {
                reconnectWithBackoff();
            } finally {
                reconnectInProgress.set(false);
            }
        });
    }

    private void reconnectWithBackoff() {
        // Add jitter to prevent thundering herd on reconnect storms
        long jitter = (long) (Math.random() * RECONNECT_JITTER_MS);
        try {
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        boolean restored = reconnectManager.attempt(() -> {
            try {
                tearDownSockets();
                connectInternal();
                resubscribeAll();
                if (reconnectRegistry != null) {
                    reconnectRegistry.notifyReconnect();
                }
                return true;
            } catch (Exception ex) {
                connected = false;
                connectionState = ConnectionState.RECONNECTING;
                return false;
            }
        });
        if (!restored) {
            connected = false;
            connectionState = ConnectionState.FAILED;
        }
    }

    private void connectInternal() {
        var authorized = feedAuthorizer.authorize();
        marketWsUri = authorized.wsUri();
        marketWs = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(marketWsUri), new MarketFeedHandler())
                .join();
        connectOrderWebSocket();
        connected = true;
        connectionState = ConnectionState.CONNECTED;
        supervisor.onConnected();
    }

    private void tearDownSockets() {
        connected = false;
        if (marketWs != null) {
            marketWs.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "");
            marketWs = null;
        }
        if (orderWs != null) {
            orderWs.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "");
            orderWs = null;
        }
        supervisor.disconnect();
    }

    private void resubscribeAll() {
        for (var entry : subscriptions.entrySet()) {
            subscribe(List.of(entry.getKey()), entry.getValue());
        }
    }

    private final class MarketFeedHandler implements java.net.http.WebSocket.Listener {
        private final ByteBuffer buffer = ByteBuffer.allocate(8192);

        @Override
        public void onOpen(java.net.http.WebSocket ws) {
            ws.request(Long.MAX_VALUE);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onBinary(java.net.http.WebSocket ws, ByteBuffer data, boolean last) {
            buffer.clear();
            buffer.put(data);
            buffer.flip();
            handleBinaryFrame(buffer);
            ws.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket ws, int statusCode, String reason) {
            connected = false;
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(java.net.http.WebSocket ws, Throwable error) {
            connected = false;
            connectionState = ConnectionState.ERROR;
            scheduleReconnect();
        }
    }

    /**
     * Handles text-based JSON messages from the Upstox portfolio stream WebSocket.
     * Messages contain order, position, and holding updates in JSON format.
     */
    private final class OrderFeedHandler implements java.net.http.WebSocket.Listener {

        @Override
        public void onOpen(java.net.http.WebSocket ws) {
            ws.request(Long.MAX_VALUE);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket ws, CharSequence data, boolean last) {
            orderMessageBuffer.append(data);
            if (last) {
                String fullMessage = orderMessageBuffer.toString();
                orderMessageBuffer.setLength(0);
                handleOrderMessage(fullMessage);
            }
            ws.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket ws, int statusCode, String reason) {
            // Don't trigger full reconnect for order WS alone — market data is more critical
            orderWs = null;
            // Best-effort reconnection
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { Thread.sleep(5_000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (!manuallyDisconnected.get() && orderWs == null) {
                    connectOrderWebSocket();
                }
            });
            return null;
        }

        @Override
        public void onError(java.net.http.WebSocket ws, Throwable error) {
            orderWs = null;
        }
    }

    private void handleOrderMessage(String message) {
        var events = portfolioStreamParser.parseMessage(message);
        for (var event : events) {
            if (event instanceof OrderUpdateEvent orderEvent && isDuplicateOrderEvent(orderEvent)) {
                continue;
            }
            for (var listener : orderUpdateListeners) {
                listener.onEvent(event);
            }
        }
    }

    private boolean isDuplicateOrderEvent(OrderUpdateEvent event) {
        String orderId = extractOrderId(event);
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        OrderStatus newStatus = extractStatus(event);
        if (newStatus == null) {
            return false;
        }
        OrderStatus previousStatus = latestOrderStatuses.put(orderId, newStatus);
        if (previousStatus == null) {
            return false;
        }
        return previousStatus == newStatus;
    }

    private static String extractOrderId(OrderUpdateEvent event) {
        return switch (event) {
            case OrderAccepted e -> e.order().orderId();
            case OrderFilled e -> e.order().orderId();
            case OrderPartiallyFilled e -> e.order().orderId();
            case OrderFullyFilled e -> e.order().orderId();
            case OrderRejected e -> e.order().orderId();
            case OrderCancelled e -> e.order().orderId();
            case OrderModified e -> e.order().orderId();
            default -> null;
        };
    }

    private static OrderStatus extractStatus(OrderUpdateEvent event) {
        return switch (event) {
            case OrderAccepted ignored -> OrderStatus.OPEN;
            case OrderFilled ignored -> OrderStatus.TRADED;
            case OrderPartiallyFilled ignored -> OrderStatus.PART_TRADED;
            case OrderFullyFilled ignored -> OrderStatus.TRADED;
            case OrderRejected ignored -> OrderStatus.REJECTED;
            case OrderCancelled ignored -> OrderStatus.CANCELLED;
            case OrderModified ignored -> OrderStatus.OPEN;
            default -> null;
        };
    }
}
