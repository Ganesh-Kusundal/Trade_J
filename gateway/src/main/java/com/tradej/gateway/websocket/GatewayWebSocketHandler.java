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
            } else {
                router.subscribe(transport, GatewayTopic.valueOf(topicName));
            }
            return;
        }

        if (GatewayBinaryCodec.isGatewayFrame(data)) {
            handleControlFrame(session, data);
        }
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
