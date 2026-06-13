package com.tradej.gateway.websocket;

import com.tradej.gateway.protocol.GatewayBinaryCodec;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.transport.SpringWebSocketTransport;
import com.tradej.gateway.transport.WebSocketTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring WebSocket handler adapter. Bridges Spring's {@link WebSocketSession} lifecycle
 * to the transport-agnostic {@link GatewayTopicRouter} via {@link SpringWebSocketTransport}.
 */
public final class GatewayWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayWebSocketHandler.class);

    private final GatewayTopicRouter router;
    private final GatewayReplayCommandProcessor replayCommandProcessor;
    private final Map<String, WebSocketTransport> transports = new ConcurrentHashMap<>();

    public GatewayWebSocketHandler(GatewayTopicRouter router) {
        this(router, null);
    }

    public GatewayWebSocketHandler(GatewayTopicRouter router, GatewayReplayCommandProcessor replayCommandProcessor) {
        this.router = router;
        this.replayCommandProcessor = replayCommandProcessor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketTransport transport = new SpringWebSocketTransport(session);
        transports.put(session.getId(), transport);
        log.info("Gateway client connected session={}", session.getId());
        router.publish(GatewayTopic.PIPELINE_HEALTH,
                GatewayBinaryCodec.utf8("connected:" + session.getId()));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        WebSocketTransport transport = transports.get(session.getId());
        if (transport == null) {
            return;
        }
        ByteBuffer buf = message.getPayload();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);

        if (data.length == 0) {
            return;
        }

        if (data.length == 1) {
            router.subscribe(transport, GatewayTopic.fromWireId(data[0]));
            return;
        }

        String text = GatewayBinaryCodec.decodeUtf8(data).trim().toUpperCase(Locale.ROOT);
        if (text.startsWith("SUBSCRIBE")) {
            String topicName = text.substring("SUBSCRIBE".length()).trim();
            if ("ALL".equals(topicName)) {
                for (GatewayTopic topic : GatewayTopic.values()) {
                    router.subscribe(transport, topic);
                }
            } else if (topicName.contains("*")) {
                // P5.2: wildcard subscription. Pattern like "MARKET_*" or
                // "POSITION_*" or "*" matches all GatewayTopic enum values
                // whose name matches the pattern (using a simple
                // case-insensitive glob: '*' is the multi-char wildcard).
                subscribeWildcard(transport, topicName);
            } else {
                router.subscribe(transport, GatewayTopic.valueOf(topicName));
            }
            return;
        }

        if (GatewayBinaryCodec.isGatewayFrame(data)) {
            handleControlFrame(session, data);
        }
    }

    /**
     * P5.2: subscribe the transport to all {@link GatewayTopic} values
     * whose {@code name()} matches the given glob pattern. The pattern
     * uses {@code *} as the multi-character wildcard. Matching is
     * case-insensitive.
     */
    private void subscribeWildcard(WebSocketTransport transport, String pattern) {
        String regex = globToRegex(pattern);
        int matched = 0;
        for (GatewayTopic topic : GatewayTopic.values()) {
            if (topic.name().matches(regex)) {
                router.subscribe(transport, topic);
                matched++;
            }
        }
        log.info("Gateway wildcard subscribe session={} pattern={} matched={} topic(s)",
                "session", pattern, matched);
    }

    /**
     * Convert a simple glob pattern (with {@code *} as multi-char wildcard)
     * to a Java regex. All other characters are matched literally.
     */
    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            } else {
                sb.append("\\").append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }

    private void handleControlFrame(WebSocketSession session, byte[] data) {
        try {
            GatewayBinaryCodec.GatewayFrame frame = GatewayBinaryCodec.decode(data);
            if (frame.topic() == GatewayTopic.REPLAY_CONTROL) {
                String payload = GatewayBinaryCodec.decodeUtf8(frame.payload());
                log.debug("Gateway replay control from session={}: {}", session.getId(), payload);
                if (replayCommandProcessor != null) {
                    replayCommandProcessor.processCommand(payload);
                }
            }
        } catch (RuntimeException e) {
            log.debug("Invalid gateway control frame session={}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WebSocketTransport transport = transports.remove(session.getId());
        if (transport != null) {
            router.unsubscribeAll(transport);
        }
        log.info("Gateway client disconnected session={} status={}", session.getId(), status);
    }
}
