package com.tradej.broker.dhan.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiEndpoints;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.websocket.feed.DhanMarketFeedBinaryParser;
import com.tradej.broker.dhan.websocket.feed.DhanMarketFeedPacket;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;

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
 * Native Java WebSocket client for Dhan live market feed ({@code wss://api-feed.dhan.co}).
 */
public final class DhanMarketFeedWebSocketClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DhanMarketFeedWebSocketClient.class);

    public interface Listener {
        default void onConnected() {
        }

        default void onDisconnected(int code, String reason) {
        }

        default void onError(Throwable error) {
        }

        void onPacket(DhanMarketFeedPacket packet, FeedMode feedMode);
    }

    record SubscriptionKey(ExchangeSegment exchangeSegment, String securityId) {
    }

    private final DhanConnectionSettings settings;
    private final DhanTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<SubscriptionKey, FeedMode> subscriptions = new ConcurrentHashMap<>();
    private final Map<SubscriptionKey, FeedMode> pendingSubscriptions = new ConcurrentHashMap<>();

    private volatile java.net.http.WebSocket webSocket;
    private volatile boolean connected;

    public DhanMarketFeedWebSocketClient(DhanConnectionSettings settings, DhanTokenProvider tokenProvider) {
        this.settings = settings;
        this.tokenProvider = tokenProvider;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public boolean isConnected() {
        return connected;
    }

    public void connect() {
        if (settings.isSandbox()) {
            throw new IllegalStateException("Dhan market feed is unavailable in sandbox mode");
        }
        tokenProvider.ensureValid();
        String url = DhanApiEndpoints.MARKET_FEED_WS_URL
                + "?version=2"
                + "&token=" + URLEncoder.encode(tokenProvider.getAccessToken(), StandardCharsets.UTF_8)
                + "&clientId=" + URLEncoder.encode(settings.clientId(), StandardCharsets.UTF_8)
                + "&authType=2";
        CompletableFuture<java.net.http.WebSocket> future = httpClient.newWebSocketBuilder()
                .header("Origin", "https://dhanhq.co")
                .buildAsync(URI.create(url), new FeedHandler());
        webSocket = future.join();
    }

    public void disconnect() {
        connected = false;
        java.net.http.WebSocket socket = webSocket;
        webSocket = null;
        if (socket != null) {
            try {
                socket.sendText("{\"RequestCode\":" + DhanProtocolConstants.FEED_DISCONNECT_REQUEST + "}", true);
            } catch (Exception ignored) {
                // best effort
            }
            socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "shutdown");
        }
        subscriptions.clear();
        pendingSubscriptions.clear();
    }

    public void subscribe(List<SubscriptionKey> instruments, FeedMode feedMode) {
        if (instruments.isEmpty()) {
            return;
        }
        if (!connected) {
            instruments.forEach(key -> pendingSubscriptions.put(key, feedMode));
            return;
        }
        List<SubscriptionKey> newKeys = instruments.stream()
                .filter(key -> !subscriptions.containsKey(key))
                .toList();
        if (subscriptions.size() + newKeys.size() > DhanProtocolConstants.FEED_MAX_INSTRUMENTS_PER_CONNECTION) {
            notifyError(new IllegalStateException("Maximum Dhan market feed subscriptions exceeded"));
            return;
        }
        instruments.forEach(key -> subscriptions.put(key, feedMode));
        sendBatched(instruments, subscribeCode(feedMode));
    }

    public void unsubscribe(List<SubscriptionKey> instruments) {
        if (!connected || instruments.isEmpty()) {
            instruments.forEach(subscriptions::remove);
            instruments.forEach(pendingSubscriptions::remove);
            return;
        }
        Map<FeedMode, List<SubscriptionKey>> grouped = new LinkedHashMap<>();
        for (SubscriptionKey key : instruments) {
            FeedMode mode = subscriptions.get(key);
            if (mode != null) {
                grouped.computeIfAbsent(mode, ignored -> new ArrayList<>()).add(key);
            }
            subscriptions.remove(key);
            pendingSubscriptions.remove(key);
        }
        grouped.forEach((mode, keys) -> sendBatched(keys, unsubscribeCode(mode)));
    }

    public Map<SubscriptionKey, FeedMode> subscriptions() {
        return Map.copyOf(subscriptions);
    }

    @Override
    public void close() {
        disconnect();
        listeners.clear();
    }

    private void sendBatched(List<SubscriptionKey> instruments, int requestCode) {
        for (int i = 0; i < instruments.size(); i += DhanProtocolConstants.FEED_MAX_INSTRUMENTS_PER_SUBSCRIPTION) {
            List<SubscriptionKey> batch = instruments.subList(
                    i,
                    Math.min(i + DhanProtocolConstants.FEED_MAX_INSTRUMENTS_PER_SUBSCRIPTION, instruments.size())
            );
            sendText(buildSubscriptionJson(requestCode, batch));
        }
    }

    private void sendText(String payload) {
        java.net.http.WebSocket socket = webSocket;
        if (socket != null) {
            socket.sendText(payload, true);
        }
    }

    private static int subscribeCode(FeedMode feedMode) {
        return switch (feedMode) {
            case TICKER -> DhanProtocolConstants.FEED_SUBSCRIBE_TICKER;
            case QUOTE -> DhanProtocolConstants.FEED_SUBSCRIBE_QUOTE;
            case FULL -> DhanProtocolConstants.FEED_SUBSCRIBE_FULL;
            default -> throw new IllegalArgumentException("Unsupported market feed mode " + feedMode);
        };
    }

    private static int unsubscribeCode(FeedMode feedMode) {
        return switch (feedMode) {
            case TICKER -> DhanProtocolConstants.FEED_UNSUBSCRIBE_TICKER;
            case QUOTE -> DhanProtocolConstants.FEED_UNSUBSCRIBE_QUOTE;
            case FULL -> DhanProtocolConstants.FEED_UNSUBSCRIBE_FULL;
            default -> throw new IllegalArgumentException("Unsupported market feed mode " + feedMode);
        };
    }

    private String buildSubscriptionJson(int requestCode, List<SubscriptionKey> instruments) {
        StringBuilder instrumentList = new StringBuilder();
        for (int i = 0; i < instruments.size(); i++) {
            SubscriptionKey key = instruments.get(i);
            if (i > 0) {
                instrumentList.append(',');
            }
            instrumentList.append("{\"ExchangeSegment\":\"")
                    .append(key.exchangeSegment().name())
                    .append("\",\"SecurityId\":\"")
                    .append(key.securityId())
                    .append("\"}");
        }
        return "{\"RequestCode\":" + requestCode
                + ",\"InstrumentCount\":" + instruments.size()
                + ",\"InstrumentList\":[" + instrumentList + "]}";
    }

    private FeedMode feedModeFor(SubscriptionKey key) {
        FeedMode mode = subscriptions.get(key);
        return mode == null ? FeedMode.TICKER : mode;
    }

    private void notifyConnected() {
        connected = true;
        listeners.forEach(Listener::onConnected);
        if (!pendingSubscriptions.isEmpty()) {
            Map<FeedMode, List<SubscriptionKey>> grouped = new LinkedHashMap<>();
            pendingSubscriptions.forEach((key, mode) ->
                    grouped.computeIfAbsent(mode, ignored -> new ArrayList<>()).add(key));
            pendingSubscriptions.clear();
            grouped.forEach((mode, keys) -> subscribe(keys, mode));
        }
    }

    private void notifyPacket(DhanMarketFeedPacket packet) {
        SubscriptionKey key = new SubscriptionKey(packet.exchangeSegment(), packet.securityId());
        FeedMode mode = feedModeFor(key);
        listeners.forEach(listener -> listener.onPacket(packet, mode));
    }

    private void notifyError(Throwable error) {
        log.debug("Dhan market feed error: {}", error.getMessage());
        listeners.forEach(listener -> listener.onError(error));
    }

    private final class FeedHandler implements java.net.http.WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            notifyConnected();
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(
                java.net.http.WebSocket webSocket,
                CharSequence data,
                boolean last
        ) {
            textBuffer.append(data);
            if (last) {
                String text = textBuffer.toString();
                textBuffer.setLength(0);
                String lower = text.toLowerCase();
                if (lower.contains("error") || lower.contains("invalid")
                        || lower.contains("unauthorized") || lower.contains("failed")) {
                    notifyError(new IllegalStateException("Dhan market feed server message: " + text));
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onBinary(
                java.net.http.WebSocket webSocket,
                ByteBuffer data,
                boolean last
        ) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            DhanMarketFeedBinaryParser.parse(bytes, packet -> notifyPacket(packet), DhanMarketFeedWebSocketClient.this::notifyError);
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
            listeners.forEach(listener -> listener.onDisconnected(statusCode, reason));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
            connected = false;
            notifyError(error);
        }
    }
}
