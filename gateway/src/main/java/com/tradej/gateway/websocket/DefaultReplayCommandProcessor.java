package com.tradej.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Processes inbound replay control commands from gateway WebSocket
 * clients. Lives in the gateway module; the application injects a
 * {@link ReplayControllerAdapter} so the gateway never has to depend
 * on the replay-engine module directly.
 */
public final class DefaultReplayCommandProcessor implements GatewayReplayCommandProcessor {

    private static final Logger log = LoggerFactory.getLogger(DefaultReplayCommandProcessor.class);

    /** Minimal seam the gateway uses to talk to the replay engine. */
    public interface ReplayControllerAdapter {
        void start(String symbol, String interval, long fromMs, long toMs);
        void pause();
        void resume();
        void step(int n);
        void stop();
    }

    private final ReplayControllerAdapter replay;
    private final ObjectMapper objectMapper;

    public DefaultReplayCommandProcessor(ReplayControllerAdapter replay, ObjectMapper objectMapper) {
        this.replay = replay;
        this.objectMapper = objectMapper;
    }

    @Override
    public void processCommand(String jsonPayload) {
        try {
            var node = (com.fasterxml.jackson.databind.node.ObjectNode)
                    objectMapper.readTree(jsonPayload);
            String op = node.path("op").asText();
            switch (op) {
                case "start" -> replay.start(
                        node.path("symbol").asText(),
                        node.path("interval").asText("1m"),
                        node.path("fromMs").asLong(),
                        node.path("toMs").asLong());
                case "pause" -> replay.pause();
                case "resume" -> replay.resume();
                case "step" -> {
                    int n = node.path("n").asInt(1);
                    for (int i = 0; i < n; i++) {
                        if (!playOneStep()) break;
                    }
                }
                case "stop" -> replay.stop();
                default -> log.warn("Unknown replay op: {}", op);
            }
        } catch (Exception e) {
            log.warn("Replay command failed: {}", e.getMessage());
        }
    }

    private boolean playOneStep() {
        // Step is a void method on the adapter. Caller can't tell when
        // the end is reached; the adapter itself decides (e.g. it can
        // throw on exhaustion, which is caught here).
        try {
            replay.step(1);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
