package com.tradej.broker.dhan.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiEndpoints;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Native Java WebSocket client for Dhan live order updates ({@code wss://api-order-update.dhan.co}).
 */
public final class DhanOrderStreamWebSocketClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DhanOrderStreamWebSocketClient.class);

    public interface Listener {
        default void onConnected() {
        }

        default void onDisconnected(int code, String reason) {
        }

        default void onError(Throwable error) {
        }

        void onOrderUpdate(JsonNode payload);

        void onTradeUpdate(JsonNode payload);
    }

    private final DhanConnectionSettings settings;
    private final DhanTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile java.net.http.WebSocket webSocket;
    private volatile boolean connected;

    public DhanOrderStreamWebSocketClient(DhanConnectionSettings settings, DhanTokenProvider tokenProvider) {
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
            throw new IllegalStateException("Dhan order stream is unavailable in sandbox mode");
        }
        tokenProvider.ensureValid();
        CompletableFuture<java.net.http.WebSocket> future = httpClient.newWebSocketBuilder()
                .header("Origin", "https://dhanhq.co")
                .buildAsync(URI.create(DhanApiEndpoints.ORDER_UPDATE_WS_URL), new OrderHandler());
        webSocket = future.join();
    }

    public void disconnect() {
        connected = false;
        java.net.http.WebSocket socket = webSocket;
        webSocket = null;
        if (socket != null) {
            socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "shutdown");
        }
    }

    @Override
    public void close() {
        disconnect();
        listeners.clear();
    }

    private void sendAuth() {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "LoginReq", Map.of(
                            "MsgCode", DhanProtocolConstants.ORDER_UPDATE_MSG_CODE,
                            "ClientId", settings.clientId(),
                            "Token", tokenProvider.getAccessToken()
                    )
            ));
            java.net.http.WebSocket socket = webSocket;
            if (socket != null) {
                socket.sendText(payload, true);
            }
        } catch (Exception ex) {
            notifyError(ex);
        }
    }

    private void handleMessage(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(text);
            if (root.has("status") || root.has("type")) {
                String status = root.path("status").asText("");
                if (!status.isBlank() && !"success".equalsIgnoreCase(status)) {
                    notifyError(new IllegalStateException("Dhan order stream auth failed: "
                            + root.path("message").asText(text)));
                }
                return;
            }
            if (root.has("exchangeTradeId")) {
                listeners.forEach(listener -> listener.onTradeUpdate(root));
                return;
            }
            if (root.has("orderId")) {
                listeners.forEach(listener -> listener.onOrderUpdate(root));
            }
        } catch (Exception ex) {
            notifyError(ex);
        }
    }

    private void notifyConnected() {
        connected = true;
        sendAuth();
        listeners.forEach(Listener::onConnected);
    }

    private void notifyError(Throwable error) {
        log.debug("Dhan order stream error: {}", error.getMessage());
        listeners.forEach(listener -> listener.onError(error));
    }

    private final class OrderHandler implements java.net.http.WebSocket.Listener {
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
                handleMessage(textBuffer.toString());
                textBuffer.setLength(0);
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
            handleMessage(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
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
