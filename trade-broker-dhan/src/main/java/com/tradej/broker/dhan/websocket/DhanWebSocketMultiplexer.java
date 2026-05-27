package com.tradej.broker.dhan.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.OrderUpdateListener;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanPayloadNormalizer;
import com.tradej.broker.dhan.mapper.DhanSdkConverters;
import com.tradej.broker.dhan.resilience.DhanBackoffUtil;
import com.tradej.broker.dhan.mapper.DhanSdkResponse;
import com.tradej.core.domain.event.BrokerAdapterError;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.StreamHealthChanged;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;
import io.github.sonicalgo.dhan.Dhan;
import io.github.sonicalgo.dhan.websocket.marketFeed.FullData;
import io.github.sonicalgo.dhan.websocket.marketFeed.IndexData;
import io.github.sonicalgo.dhan.websocket.marketFeed.MarketFeedClient;
import io.github.sonicalgo.dhan.websocket.marketFeed.MarketFeedListener;
import io.github.sonicalgo.dhan.websocket.marketFeed.QuoteData;
import io.github.sonicalgo.dhan.websocket.marketFeed.TickerData;
import io.github.sonicalgo.dhan.websocket.order.OrderStreamClient;
import io.github.sonicalgo.dhan.websocket.order.OrderStreamListener;
import io.github.sonicalgo.dhan.websocket.order.OrderUpdate;
import io.github.sonicalgo.dhan.websocket.order.TradeUpdate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class DhanWebSocketMultiplexer implements WebSocketMultiplexer {
    private static final int INVALID_TOKEN_CODE = DhanProtocolConstants.INVALID_TOKEN_CODE;
    private static final long STALE_FEED_THRESHOLD_MS = DhanProtocolConstants.STALE_FEED_THRESHOLD_MS;
    private static final long TOKEN_CHECK_INTERVAL_MS = DhanProtocolConstants.TOKEN_CHECK_INTERVAL_MS;

    private final DhanClientHolder clientHolder;
    private final DhanInstrumentResolver resolver;
    private final DhanConnectionSettings settings;
    private final DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer();
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

    private volatile MarketFeedClient marketFeedClient;
    private volatile OrderStreamClient orderStreamClient;
    private volatile boolean connected;
    private volatile boolean shutdown;
    private volatile long lastMarketEventAtMs;
    private volatile long lastTokenCheckAtMs;
    private volatile boolean staleFeedEmitted;

    // Reconnection circuit breaker state (accessed under transportLock)
    private int reconnectAttempts;
    private long circuitOpenUntilMs;

    // ---------------------------------------------------------------
    // Event factory helpers — reduce EventMetadata.root() boilerplate
    // ---------------------------------------------------------------

    private static StreamHealthChanged healthEvent(String stream, String status, int detail) {
        return new StreamHealthChanged(EventMetadata.root(), stream, status, detail);
    }

    private static BrokerAdapterError brokerError(String source, String message) {
        return new BrokerAdapterError(EventMetadata.root(), "dhan", source, message);
    }

    public DhanWebSocketMultiplexer(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanConnectionSettings settings
    ) {
        this.clientHolder = clientHolder;
        this.resolver = resolver;
        this.settings = settings;
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
            marketFeedClient.connect(false);
            orderStreamClient.connect(false);
        }
    }

    @Override
    public void disconnect() {
        shutdown = true;
        connected = false;
        synchronized (transportLock) {
            closeClientsLocked();
        }
        healthMonitor.shutdownNow();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
        List<io.github.sonicalgo.dhan.websocket.marketFeed.Instrument> sdkInstruments = instruments.stream()
                .map(this::toSdkInstrument)
                .toList();
        if (sdkInstruments.isEmpty()) {
            throw new IllegalArgumentException("Cannot subscribe an empty instrument set");
        }
        synchronized (transportLock) {
            ensureClientsLocked();
            marketFeedClient.subscribe(sdkInstruments, toSdkFeedMode(feedMode));
        }
        instruments.forEach(request -> subscriptions.put(request, feedMode));
        lastMarketEventAtMs = System.currentTimeMillis();
        staleFeedEmitted = false;
    }

    @Override
    public void unsubscribe(Collection<MarketSubscriptionRequest> instruments) {
        synchronized (transportLock) {
            if (marketFeedClient != null) {
                marketFeedClient.unsubscribe(instruments.stream().map(this::toSdkInstrument).toList());
            }
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
        bindClientsLocked(clientHolder.client());
    }

    private void bindClientsLocked(Dhan dhan) {
        MarketFeedClient newMarketFeedClient = dhan.createMarketFeedClient(
                settings.maxReconnectAttempts(),
                settings.autoReconnectEnabled(),
                settings.autoResubscribeEnabled()
        );
        OrderStreamClient newOrderStreamClient = dhan.createOrderStreamClient(
                settings.maxReconnectAttempts(),
                settings.autoReconnectEnabled()
        );
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

    private void rebindAfterTokenRotation(Dhan newDhan) {
        synchronized (transportLock) {
            if (shutdown) {
                return;
            }
            boolean shouldReconnect = connected || !subscriptions.isEmpty();
            closeClientsLocked();
            bindClientsLocked(newDhan);
            if (shouldReconnect) {
                staleFeedEmitted = false;
                resetLifecycleTimestampsLocked();
                resetReconnectCircuitLocked();
                marketFeedClient.connect(false);
                orderStreamClient.connect(false);
            }
        }
    }

    private void wireListeners(MarketFeedClient marketFeedClient, OrderStreamClient orderStreamClient) {
        marketFeedClient.addListener(new MarketFeedListener() {
            @Override
            public void onConnected() {
                connected = true;
                lastMarketEventAtMs = System.currentTimeMillis();
                lastTokenCheckAtMs = lastMarketEventAtMs;
                staleFeedEmitted = false;
                resubscribeAll();
                publishMarket(healthEvent("dhan", "CONNECTED", 0));
            }

            @Override
            public void onReconnected() {
                connected = true;
                lastMarketEventAtMs = System.currentTimeMillis();
                lastTokenCheckAtMs = lastMarketEventAtMs;
                staleFeedEmitted = false;
                resubscribeAll();
                publishMarket(healthEvent("dhan", "RECONNECTED", 0));
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
            public void onTickerData(TickerData data) {
                handleMarketPayload(data, false);
            }

            @Override
            public void onQuoteData(QuoteData data) {
                handleMarketPayload(data, true);
            }

            @Override
            public void onFullData(FullData data) {
                handleMarketPayload(data, true);
            }

            @Override
            public void onIndexData(IndexData data) {
                handleMarketPayload(data, false);
            }

            @Override
            public void onReconnecting(int attempt, long delayMs) {
                publishMarket(healthEvent("dhan", "RECONNECTING", attempt));
            }
        });

        orderStreamClient.addListener(new OrderStreamListener() {
            @Override
            public void onConnected() {
                publishOrder(healthEvent("dhan-order", "CONNECTED", 0));
            }

            @Override
            public void onReconnected() {
                publishOrder(healthEvent("dhan-order", "RECONNECTED", 0));
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
            public void onOrderUpdate(OrderUpdate update) {
                try {
                    DhanSdkResponse<?> response = new DhanSdkResponse<>(update);
                    DhanInstrumentDefinition definition = lookupDefinition(response);
                    Order order = normalizer.normalizeOrder(response, definition);
                    OrderStatus previousStatus = latestOrderStatuses.put(order.orderId(), order.status());
                    if (order.status().isRejected() && previousStatus != OrderStatus.REJECTED) {
                        publishOrder(new OrderRejected(EventMetadata.correlated(order.correlationId(), 0), order, order.rejectionReason()));
                    } else if (previousStatus == null) {
                        publishOrder(new OrderAccepted(EventMetadata.correlated(order.correlationId(), 0), order));
                    }
                } catch (RuntimeException ex) {
                    publishOrder(brokerError("order-normalization", ex.getMessage()));
                }
            }

            @Override
            public void onTradeUpdate(TradeUpdate update) {
                try {
                    DhanSdkResponse<?> response = new DhanSdkResponse<>(update);
                    DhanInstrumentDefinition definition = lookupDefinition(response);
                    Trade trade = normalizer.normalizeTrade(response, definition);
                    Order order = normalizer.normalizeOrder(response, definition);
                    latestOrderStatuses.put(order.orderId(), order.status());
                    publishOrder(new OrderFilled(EventMetadata.correlated(order.correlationId(), 0), order, List.of(trade)));
                } catch (RuntimeException ex) {
                    publishOrder(brokerError("trade-normalization", ex.getMessage()));
                }
            }

            @Override
            public void onReconnecting(int attempt, long delayMs) {
                publishOrder(healthEvent("dhan-order", "RECONNECTING", attempt));
            }
        });
    }

    private void handleMarketPayload(Object source, boolean includeDepth) {
        try {
            DhanSdkResponse<?> response = new DhanSdkResponse<>(source);
            DhanInstrumentDefinition definition = lookupDefinition(response);
            TickReceived normalized = normalizer.normalizeTick(response, definition);
            TickReceived tick = includeDepth
                    ? normalized
                    : new TickReceived(
                    normalized.metadata(),
                    normalized.symbol(),
                    normalized.interval(),
                    normalized.ltpPaisa(),
                    normalized.lastTradeQuantity(),
                    normalized.cumulativeVolume(),
                    normalized.exchangeTimestampMs(),
                    null
            );
            lastMarketEventAtMs = System.currentTimeMillis();
            staleFeedEmitted = false;
            publishMarket(tick);
        } catch (RuntimeException ex) {
            publishMarket(brokerError("market-normalization", ex.getMessage()));
        }
    }

    private DhanInstrumentDefinition lookupDefinition(DhanSdkResponse<?> source) {
        return resolver.resolveDhanPayload(source);
    }

    private io.github.sonicalgo.dhan.websocket.marketFeed.Instrument toSdkInstrument(MarketSubscriptionRequest request) {
        DhanInstrumentDefinition definition = resolver.requireDhanDefinition(request.symbol(), request.exchangeSegment());
        return new io.github.sonicalgo.dhan.websocket.marketFeed.Instrument(
                DhanSdkConverters.segment(definition.exchangeSegment()),
                definition.securityId()
        );
    }

    private io.github.sonicalgo.dhan.websocket.marketFeed.FeedMode toSdkFeedMode(FeedMode feedMode) {
        return switch (feedMode) {
            case TICKER -> io.github.sonicalgo.dhan.websocket.marketFeed.FeedMode.TICKER;
            case QUOTE, DEPTH_20 -> io.github.sonicalgo.dhan.websocket.marketFeed.FeedMode.QUOTE;
            case FULL -> io.github.sonicalgo.dhan.websocket.marketFeed.FeedMode.FULL;
            case DEPTH_200 -> throw new IllegalArgumentException("Dhan does not expose DEPTH_200 over the configured market feed transport");
        };
    }

    private void resubscribeAll() {
        subscriptions.entrySet().stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue))
                .forEach((feedMode, grouped) -> subscribe(grouped.stream().map(Map.Entry::getKey).toList(), feedMode));
    }

    private void verifyFeedLiveness() {
        if (shutdown || subscriptions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTokenCheckAtMs >= TOKEN_CHECK_INTERVAL_MS) {
            try {
                clientHolder.client();
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
            delayMs = DhanBackoffUtil.computeDelayMs(
                    reconnectAttempts,
                    DhanProtocolConstants.WS_RECONNECT_BASE_DELAY_MS,
                    DhanProtocolConstants.WS_RECONNECT_MAX_DELAY_MS
            );
            closeClientsLocked();
            try {
                bindClientsLocked(clientHolder.client());
            } catch (RuntimeException ex) {
                publishMarket(brokerError("client-rebuild", ex.getMessage()));
                return;
            }
        }
        // Sleep outside lock to avoid blocking the health monitor
        sleepQuietly(delayMs);
        synchronized (transportLock) {
            if (shutdown) {
                return;
            }
            resetLifecycleTimestampsLocked();
            marketFeedClient.connect(false);
            orderStreamClient.connect(false);
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

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void publishMarket(DomainEvent event) {
        marketListeners.forEach(listener -> listener.onEvent(event));
    }

    private void publishOrder(DomainEvent event) {
        orderListeners.forEach(listener -> listener.onEvent(event));
    }
}
