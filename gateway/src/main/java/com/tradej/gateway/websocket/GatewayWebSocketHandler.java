package com.tradej.gateway.websocket;

import com.tradej.gateway.protocol.GatewayBinaryCodec;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * WebSocket handler for the gateway. Accepts binary messages for subscription
 * control and text-based topic subscription commands.
 *
 * Protocol:
 * - Single-byte message: interpreted as {@link GatewayTopic#fromWireId(int)} for subscribe
 * - Text message starting with "SUBSCRIBE": subscribes to the named topic
 * - "SUBSCRIBE ALL": subscribes to all topics
 * - Binary gateway frames: handled as control frames (e.g. REPLAY_CONTROL)
 */
public final class GatewayWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayWebSocketHandler.class);

    private final GatewayTopicRouter router;
    private final GatewayReplayCommandProcessor replayCommandProcessor;

    public GatewayWebSocketHandler(GatewayTopicRouter router) {
        this(router, null);
    }

    public GatewayWebSocketHandler(GatewayTopicRouter router, GatewayReplayCommandProcessor replayCommandProcessor) {
        this.router = router;
        this.replayCommandProcessor = replayCommandProcessor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Gateway client connected session={}", session.getId());
        router.publish(GatewayTopic.PIPELINE_HEALTH,
                GatewayBinaryCodec.utf8("connected:" + session.getId()));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer buf = (ByteBuffer) message.getPayload();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);

        if (data.length == 0) {
            return;
        }

        if (data.length == 1) {
            // Single-byte subscribe by wire ID
            router.subscribe(session, GatewayTopic.fromWireId(data[0]));
            return;
        }

        // Text-based subscription command
        String text = GatewayBinaryCodec.decodeUtf8(data).trim().toUpperCase(Locale.ROOT);
        if (text.startsWith("SUBSCRIBE")) {
            String topicName = text.substring("SUBSCRIBE".length()).trim();
            if ("ALL".equals(topicName)) {
                for (GatewayTopic topic : GatewayTopic.values()) {
                    router.subscribe(session, topic);
                }
            } else {
                router.subscribe(session, GatewayTopic.valueOf(topicName));
            }
            return;
        }

        if (GatewayBinaryCodec.isGatewayFrame(data)) {
            handleControlFrame(session, data);
            return;
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
        router.unsubscribeAll(session);
        log.info("Gateway client disconnected session={} status={}", session.getId(), status);
    }
}
