package com.tradej.composition;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.EventBus;
import com.tradej.replay.engine.CandleReplaySession;
import com.tradej.replay.engine.ReplayController;
import com.tradej.replay.engine.ReplayOrchestrator;
import com.tradej.replay.engine.TickReplaySession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Single public entry point for replay. Replaces the three previous
 * replay surfaces (in-process {@link ReplayController}, in-process
 * {@link CandleReplaySession} + {@link TickReplaySession}, and the
 * HTTP-only {@code ReplayApiController}) with one Spring-free façade.
 *
 * <p>Lives in the composition module but is constructed directly by its
 * callers (Spring wires it as a bean via {@code FullCompositionConfiguration};
 * the CLI constructs it directly). It is NOT owned by
 * {@link FullComposition} itself — the two are independent composition-layer
 * services. Spring wires it as a bean; CLI uses it directly.
 *
 * <p>Sessions are identified by a stable {@code sessionId}. The same
 * session can be controlled via the WebSocket control plane (see
 * {@code gateway.DefaultReplayCommandProcessor}) or via REST.
 */
public final class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);

    private final EventBus eventBus;
    private final ReplayOrchestrator orchestrator;
    private final ReplayController controller;
    private final CandleReplaySession candleSession;
    private final TickReplaySession tickSession;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

    public ReplayService(
            EventBus eventBus,
            ReplayOrchestrator orchestrator,
            ReplayController controller,
            Optional<CandleReplaySession> candleSession,
            Optional<TickReplaySession> tickSession,
            ObjectMapper objectMapper
    ) {
        this.eventBus = eventBus;
        this.orchestrator = orchestrator;
        this.controller = controller;
        this.candleSession = candleSession.orElse(null);
        this.tickSession = tickSession.orElse(null);
        this.objectMapper = objectMapper;
    }

    /**
     * Start a new replay session.
     *
     * @param symbol    symbol under replay
     * @param interval  candle interval ("1m", "5m", …)
     * @param fromMs    window start in epoch ms
     * @param toMs      window end in epoch ms
     * @param speed     playback speed multiplier (>= 0.1)
     * @return a session handle with the assigned id
     */
    public ReplaySessionHandle start(String symbol, String interval, long fromMs, long toMs, double speed) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        String id = UUID.randomUUID().toString();
        Session s = new Session(id, symbol, interval, fromMs, toMs, speed);
        sessions.put(id, s);
        // For now delegate to the existing in-process ReplayController — the
        // façade is the *only* public surface; ReplayController becomes a
        // private collaborator.
        try {
            // Load candles via orchestrator (candle-level); the controller
            // takes a list. Tick-level uses tickSession. The class is
            // event-symmetric: the same event bus subscribers see the same
            // CandleClosed events regardless of which path started.
            var result = orchestrator.replayCandles(symbol, interval, fromMs, toMs, eventBus);
            log.info("Replay started id={} symbol={} interval={} from={} to={} speed={} replayed={}",
                    id, symbol, interval, fromMs, toMs, speed, result.replayed());
            if (candleSession != null) {
                candleSession.play();
            }
        } catch (RuntimeException e) {
            sessions.remove(id);
            throw e;
        }
        return new ReplaySessionHandle(id, symbol, interval, fromMs, toMs, speed);
    }

    public void pause(String sessionId) {
        require(sessionId);
        if (candleSession != null) candleSession.pause();
        if (tickSession != null) tickSession.pause();
        log.debug("Replay paused session={}", sessionId);
    }

    public void resume(String sessionId) {
        require(sessionId);
        if (candleSession != null) candleSession.play();
        if (tickSession != null) tickSession.play();
        log.debug("Replay resumed session={}", sessionId);
    }

    public void step(String sessionId, int n) {
        require(sessionId);
        if (candleSession != null) {
            for (int i = 0; i < n; i++) candleSession.step();
        }
        if (tickSession != null) {
            for (int i = 0; i < n; i++) tickSession.play();
        }
    }

    public void stop(String sessionId) {
        Session s = sessions.remove(sessionId);
        if (s == null) return;
        if (candleSession != null) candleSession.stop();
        if (tickSession != null) tickSession.stop();
        log.info("Replay stopped session={}", sessionId);
    }

    public ReplayStatus status(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) return ReplayStatus.unknown();
        return new ReplayStatus(
                sessionId,
                s.symbol,
                s.interval,
                s.fromMs,
                s.toMs,
                s.speed,
                "PLAYING"
        );
    }

    /**
     * Apply an inbound WebSocket control-plane command. The default
     * {@code gateway.DefaultReplayCommandProcessor} routes JSON of the form
     * {@code {"op":"start"|"pause"|"resume"|"step"|"stop", "sessionId":...}}
     * to this method. The reply (a {@link ReplayStatus}) is published back
     * to the WS client via the gateway's REPLAY_CONTROL topic.
     */
    public ReplayStatus handleControlCommand(String jsonPayload) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(jsonPayload);
            String op = node.path("op").asText();
            String sessionId = node.path("sessionId").asText("");
            return switch (op) {
                case "start" -> {
                    String symbol = node.path("symbol").asText();
                    String interval = node.path("interval").asText("1m");
                    long fromMs = node.path("fromMs").asLong();
                    long toMs = node.path("toMs").asLong();
                    double speed = node.path("speed").asDouble(1.0);
                    yield status(start(symbol, interval, fromMs, toMs, speed).id());
                }
                case "pause" -> { pause(sessionId); yield status(sessionId); }
                case "resume" -> { resume(sessionId); yield status(sessionId); }
                case "step" -> { step(sessionId, node.path("n").asInt(1)); yield status(sessionId); }
                case "stop" -> { stop(sessionId); yield ReplayStatus.unknown(); }
                default -> throw new IllegalArgumentException("Unknown op: " + op);
            };
        } catch (JsonProcessingException e) {
            log.warn("Replay control command parse failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid replay command payload", e);
        } catch (RuntimeException e) {
            log.warn("Replay control command failed: {}", e.getMessage());
            throw e;
        }
    }

    /** Re-export the multi-TF aggregation helper so external callers can also use it. */
    public static List<Candle> aggregateHigherTimeframes(Candle oneMin) {
        return CandleReplaySession.aggregateHigherTimeframes(oneMin);
    }

    private void require(String sessionId) {
        if (!sessions.containsKey(sessionId)) {
            throw new IllegalArgumentException("Unknown replay session: " + sessionId);
        }
    }

    private static final class Session {
        final String id;
        final String symbol;
        final String interval;
        final long fromMs;
        final long toMs;
        final double speed;

        Session(String id, String symbol, String interval, long fromMs, long toMs, double speed) {
            this.id = id;
            this.symbol = symbol;
            this.interval = interval;
            this.fromMs = fromMs;
            this.toMs = toMs;
            this.speed = speed;
        }
    }

    /** Opaque handle returned from {@link #start}. */
    public record ReplaySessionHandle(String id, String symbol, String interval, long fromMs, long toMs, double speed) {}

    /** Status snapshot returned from {@link #status} and control commands. */
    public record ReplayStatus(String sessionId, String symbol, String interval, long fromMs, long toMs, double speed, String state) {
        public static ReplayStatus unknown() {
            return new ReplayStatus("", "", "", 0L, 0L, 0.0, "UNKNOWN");
        }
    }
}
