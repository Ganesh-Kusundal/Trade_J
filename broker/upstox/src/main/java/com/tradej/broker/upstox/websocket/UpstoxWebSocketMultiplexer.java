package com.tradej.broker.upstox.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.OrderUpdateListener;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.websocket.DefaultWebSocketSupervisor;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;

import com.tradej.core.domain.value.FeedMode;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class UpstoxWebSocketMultiplexer implements WebSocketMultiplexer {

    private static final long STALE_THRESHOLD_MS = 30_000L;
    private static final long HEALTH_CHECK_INTERVAL_MS = 5_000L;

    private final UpstoxFeedAuthorizer feedAuthorizer;
    private final UpstoxStreamNormalizer streamNormalizer;
    private final UpstoxInstrumentResolver instrumentResolver;
    private final EventMetadataFactory metadataFactory;
    private final DefaultWebSocketSupervisor supervisor;
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    private final ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<MarketDataListener> marketDataListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderUpdateListener> orderUpdateListeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService healthExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "upstox-feed-health"));

    private volatile java.net.http.WebSocket marketWs;
    private volatile java.net.http.WebSocket orderWs;
    private volatile boolean connected;
    private volatile String currentWsUri = "";

    public UpstoxWebSocketMultiplexer(
            UpstoxFeedAuthorizer feedAuthorizer,
            UpstoxStreamNormalizer streamNormalizer,
            UpstoxInstrumentResolver instrumentResolver,
            EventMetadataFactory metadataFactory
    ) {
        this.feedAuthorizer = feedAuthorizer;
        this.streamNormalizer = streamNormalizer;
        this.instrumentResolver = instrumentResolver;
        this.metadataFactory = metadataFactory;
        this.supervisor = new DefaultWebSocketSupervisor(STALE_THRESHOLD_MS);
    }

    @Override
    public void connect() {
        try {
            var authorized = feedAuthorizer.authorize();
            currentWsUri = authorized.wsUri();
            // Establish WebSocket connection using JDK HttpClient
            var httpClient = java.net.http.HttpClient.newHttpClient();
            marketWs = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(currentWsUri), new MarketFeedHandler())
                    .join();
            connected = true;
            supervisor.onConnected();
            healthExecutor.scheduleAtFixedRate(this::checkHealth,
                    HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect Upstox WebSocket", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
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
        // Send subscription message over WebSocket
        // Format depends on Upstox feed V3 protocol — needs sandbox verification
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
            if (frame == null) return; // heartbeat/disconnect
            supervisor.onMessage(buffer);
            long sequenceId = sequenceCounter.incrementAndGet();
            MarketSubscriptionRequest key = findKey(frame.instrumentToken());
            FeedMode feedMode = key != null ? subscriptions.getOrDefault(key, FeedMode.TICKER) : FeedMode.TICKER;
            MarketTickEvent event = streamNormalizer.toMarketTick(frame, feedMode, sequenceId);
            for (var listener : marketDataListeners) {
                listener.onEvent(event);
            }
        } catch (UpstoxBinaryParser.UpstoxParserException e) {
            // Log malformed frame and continue
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
            reconnect();
        }
    }

    private void reconnect() {
        disconnect();
        connect();
        // Resubscribe
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
        public void onError(java.net.http.WebSocket ws, Throwable error) {
            supervisor.onError(error);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket ws, int statusCode, String reason) {
            supervisor.onClose(statusCode, reason);
            connected = false;
            return null;
        }
    }
}
