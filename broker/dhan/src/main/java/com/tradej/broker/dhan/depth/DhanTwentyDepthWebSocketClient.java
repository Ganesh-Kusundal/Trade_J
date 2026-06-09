package com.tradej.broker.dhan.depth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanApiConverters;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.value.ExchangeSegment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Dedicated WebSocket transport for Dhan 20-level market depth.
 */
public final class DhanTwentyDepthWebSocketClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DhanTwentyDepthWebSocketClient.class);
    // Default URL (live), can be overridden via DhanConnectionSettings
    private static final String DEFAULT_DEPTH_WS_URL = "wss://depth-api-feed.dhan.co/twentydepth";
    private static final int SUBSCRIBE_REQUEST_CODE = 23;
    private static final int DISCONNECT_REQUEST_CODE = 12;

    private final DhanConnectionSettings settings;
    private final DhanTokenProvider tokenProvider;
    private final DhanInstrumentResolver resolver;
    private final EventMetadataFactory metadataFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final CopyOnWriteArrayList<Consumer<DepthUpdateEvent>> listeners = new CopyOnWriteArrayList<>();
    private final Map<DepthBookKey, PendingDepthBook> pendingBooks = new ConcurrentHashMap<>();
    private final Map<MarketSubscriptionRequest, Boolean> subscriptions = new ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService reconnectScheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dhan-depth-reconnect");
                t.setDaemon(true);
                return t;
            });

    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_BASE_DELAY_MS = 1_000L;
    private static final long RECONNECT_MAX_DELAY_MS = 30_000L;

    private volatile java.net.http.WebSocket webSocket;
    private volatile boolean connected;
    private volatile int reconnectAttempts;
    private volatile boolean manuallyDisconnected;
    private volatile long lastDepthMessageTimestamp = 0L;
    private static final long STALE_THRESHOLD_MS = 30_000L;
    private static final long HEALTH_CHECK_INTERVAL_MS = 5_000L;
    private final java.util.concurrent.ScheduledExecutorService healthExecutor =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dhan-depth-health");
                t.setDaemon(true);
                return t;
            });

    public DhanTwentyDepthWebSocketClient(
            DhanConnectionSettings settings,
            DhanTokenProvider tokenProvider,
            DhanInstrumentResolver resolver,
            EventMetadataFactory metadataFactory
    ) {
        this.settings = settings;
        this.tokenProvider = tokenProvider;
        this.resolver = resolver;
        this.metadataFactory = metadataFactory;
    }

    public void onDepthUpdate(Consumer<DepthUpdateEvent> listener) {
        listeners.add(listener);
    }

    public boolean isConnected() {
        return connected;
    }

    public void connect() {
        manuallyDisconnected = false;
        if (settings.isSandbox() && !settings.killSwitchTestEnabled()) {
            throw new IllegalStateException("Dhan 20-level depth feed is unavailable in sandbox mode");
        }
        tokenProvider.ensureValid();
        String url = settings.depthWsUrl()
                + "?token=" + URLEncoder.encode(tokenProvider.getAccessToken(), StandardCharsets.UTF_8)
                + "&clientId=" + URLEncoder.encode(settings.clientId(), StandardCharsets.UTF_8)
                + "&authType=2";
        CompletableFuture<java.net.http.WebSocket> future = httpClient.newWebSocketBuilder()
                .header("Origin", "https://dhanhq.co")
                .buildAsync(URI.create(url), new DepthFeedHandler());
        webSocket = future.join();
        lastDepthMessageTimestamp = System.currentTimeMillis();
        healthExecutor.scheduleAtFixedRate(this::checkStaleness,
                HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void disconnect() {
        manuallyDisconnected = true;
        connected = false;
        java.net.http.WebSocket socket = webSocket;
        webSocket = null;
        if (socket != null) {
            try {
                socket.sendText(objectMapper.writeValueAsString(Map.of("RequestCode", DISCONNECT_REQUEST_CODE)), true);
            } catch (Exception ignored) {
                // best effort
            }
            socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "shutdown");
        }
        pendingBooks.clear();
        healthExecutor.shutdown();
        reconnectScheduler.shutdown();
    }

    private void scheduleReconnect() {
        if (manuallyDisconnected || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log.warn("Dhan depth reconnect exhausted after {} attempts", reconnectAttempts);
            return;
        }
        reconnectAttempts++;
        long delay = Math.min(RECONNECT_BASE_DELAY_MS * (1L << (reconnectAttempts - 1)), RECONNECT_MAX_DELAY_MS);
        log.info("Scheduling Dhan depth reconnect attempt {} in {}ms", reconnectAttempts, delay);
        reconnectScheduler.schedule(() -> {
            try {
                connect();
                reconnectAttempts = 0;
                log.info("Dhan depth reconnected successfully");
            } catch (Exception ex) {
                log.warn("Dhan depth reconnect attempt {} failed: {}", reconnectAttempts, ex.getMessage());
                scheduleReconnect();
            }
        }, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void subscribe(List<MarketSubscriptionRequest> instruments) {
        if (instruments.isEmpty()) {
            return;
        }
        instruments.forEach(request -> subscriptions.put(request, Boolean.TRUE));
        java.net.http.WebSocket socket = webSocket;
        if (socket == null) {
            return;
        }
        sendSubscribe(socket, instruments);
    }

    public void unsubscribe(List<MarketSubscriptionRequest> instruments) {
        instruments.forEach(subscriptions::remove);
    }

    public void resubscribeAll() {
        if (subscriptions.isEmpty()) {
            return;
        }
        java.net.http.WebSocket socket = webSocket;
        if (socket == null) {
            return;
        }
        sendSubscribe(socket, List.copyOf(subscriptions.keySet()));
    }

    @Override
    public void close() {
        disconnect();
    }

    private void sendSubscribe(java.net.http.WebSocket socket, List<MarketSubscriptionRequest> instruments) {
        try {
            List<Map<String, String>> instrumentList = new ArrayList<>(instruments.size());
            for (MarketSubscriptionRequest request : instruments) {
                DhanInstrumentDefinition definition = resolver.requireDhanDefinition(request.symbol(), request.exchangeSegment());
                instrumentList.add(Map.of(
                        "ExchangeSegment", DhanApiConverters.segment(definition.exchangeSegment()),
                        "SecurityId", definition.securityId()
                ));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("RequestCode", SUBSCRIBE_REQUEST_CODE);
            payload.put("InstrumentCount", instrumentList.size());
            payload.put("InstrumentList", instrumentList);
            socket.sendText(objectMapper.writeValueAsString(payload), true);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to subscribe to Dhan 20-level depth feed", ex);
        }
    }

    private void handleBinary(byte[] payload) {
        List<DhanTwentyDepthBinaryParser.DepthSidePacket> sides = DhanTwentyDepthBinaryParser.parse(payload);
        if (sides.isEmpty()) {
            log.debug("Ignoring empty or unparseable twentydepth payload ({} bytes)", payload.length);
            return;
        }
        for (DhanTwentyDepthBinaryParser.DepthSidePacket side : sides) {
            DepthBookKey key = new DepthBookKey(side.segment(), side.securityId());
            PendingDepthBook book = pendingBooks.computeIfAbsent(key, ignored -> new PendingDepthBook());
            if (side.feedCode() == DhanTwentyDepthBinaryParser.BID_FEED_CODE) {
                book.bids = side.levels();
            } else if (side.feedCode() == DhanTwentyDepthBinaryParser.ASK_FEED_CODE) {
                book.asks = side.levels();
            } else {
                log.debug("Skipping unknown twentydepth feed code {}", side.feedCode());
                continue;
            }
            publishDepth(key, book);
        }
    }

    private void publishDepth(DepthBookKey key, PendingDepthBook book) {
        List<DepthLevel> bids = book.bids == null ? List.of() : book.bids;
        List<DepthLevel> asks = book.asks == null ? List.of() : book.asks;
        if (bids.isEmpty() && asks.isEmpty()) {
            return;
        }
        DhanInstrumentDefinition definition = resolver.requireSecurityId(key.securityId());
        ExchangeSegment segment = definition.exchangeSegment();
        if (segment != key.segment()) {
            segment = key.segment();
        }
        int levels = Math.max(bids.size(), asks.size());
        DepthUpdateEvent event = new DepthUpdateEvent(
                metadataFactory.root(),
                definition.canonicalSymbol(),
                segment,
                bids,
                asks,
                levels,
                System.currentTimeMillis()
        );
        listeners.forEach(listener -> listener.accept(event));
    }

    private final class DepthFeedHandler implements java.net.http.WebSocket.Listener {
        private ByteBuffer fragmentBuffer = ByteBuffer.allocate(16_384);

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            connected = true;
            resubscribeAll();
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(
                java.net.http.WebSocket webSocket,
                CharSequence data,
                boolean last
        ) {
            log.warn("Dhan twentydepth text frame: {}", data);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onBinary(
                java.net.http.WebSocket webSocket,
                ByteBuffer data,
                boolean last
        ) {
            appendFragment(data, last);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(
                java.net.http.WebSocket webSocket,
                int statusCode,
                String reason
        ) {
            connected = false;
            if (!manuallyDisconnected) {
                log.warn("Dhan depth WebSocket closed unexpectedly (code={}, reason={}), scheduling reconnect", statusCode, reason);
                scheduleReconnect();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
            connected = false;
            if (!manuallyDisconnected) {
                log.warn("Dhan depth WebSocket error: {}, scheduling reconnect", error.getMessage());
                scheduleReconnect();
            }
        }

        private void appendFragment(ByteBuffer data, boolean last) {
            if (fragmentBuffer.remaining() < data.remaining()) {
                ByteBuffer expanded = ByteBuffer.allocate(fragmentBuffer.capacity() * 2);
                fragmentBuffer.flip();
                expanded.put(fragmentBuffer);
                fragmentBuffer.clear();
                fragmentBuffer = expanded;
            }
            fragmentBuffer.put(data);
            if (last) {
                lastDepthMessageTimestamp = System.currentTimeMillis();
                fragmentBuffer.flip();
                byte[] payload = new byte[fragmentBuffer.remaining()];
                fragmentBuffer.get(payload);
                fragmentBuffer.clear();
                handleBinary(payload);
            }
        }
    }

    private void checkStaleness() {
        if (connected && lastDepthMessageTimestamp > 0) {
            long idle = System.currentTimeMillis() - lastDepthMessageTimestamp;
            if (idle > STALE_THRESHOLD_MS) {
                log.warn("Dhan depth feed stale ({}ms idle), forcing reconnect", idle);
                java.net.http.WebSocket socket = webSocket;
                if (socket != null) {
                    socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "stale");
                }
            }
        }
    }

    private record DepthBookKey(ExchangeSegment segment, String securityId) {
    }

    private static final class PendingDepthBook {
        private List<DepthLevel> bids;
        private List<DepthLevel> asks;
    }

}
