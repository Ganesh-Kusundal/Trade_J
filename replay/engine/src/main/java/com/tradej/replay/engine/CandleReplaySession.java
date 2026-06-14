package com.tradej.replay.engine;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.market.CandleBucketPolicy;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * In-app candle replay session (play/pause/step/speed) with gateway status broadcasts.
 */
public final class CandleReplaySession {

    private static final Logger log = LoggerFactory.getLogger(CandleReplaySession.class);

    public enum ReplayState {
        STOPPED,
        PLAYING,
        PAUSED
    }

    private final Optional<EventBus> eventBus;
    private final Optional<GatewayTopicRouter> gatewayRouter;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "candle-replay");
        t.setDaemon(true);
        return t;
    });

    private List<Candle> candles = List.of();
    private int currentIndex;
    private ReplayState state = ReplayState.STOPPED;
    private double speedMultiplier = 1.0;
    private long currentTimeMs;
    private ScheduledFuture<?> playFuture;

    public CandleReplaySession(
            Optional<EventBus> eventBus,
            Optional<GatewayTopicRouter> gatewayRouter,
            ObjectMapper objectMapper
    ) {
        this.eventBus = eventBus;
        this.gatewayRouter = gatewayRouter;
        this.objectMapper = objectMapper;
    }

    public synchronized void start(List<Candle> replayCandles) {
        stop();
        if (replayCandles == null || replayCandles.isEmpty()) {
            throw new IllegalArgumentException("Replay requires at least one candle");
        }
        List<Candle> sorted = new ArrayList<>(replayCandles);
        sorted.sort((a, b) -> Long.compare(a.startTimeMs(), b.startTimeMs()));
        this.candles = List.copyOf(sorted);
        this.currentIndex = 0;
        this.currentTimeMs = sorted.getFirst().startTimeMs();
        this.state = ReplayState.PAUSED;
        publishStatus();
        log.info("Candle replay initialized candles={}", candles.size());
    }

    public synchronized void play() {
        if (state == ReplayState.STOPPED) {
            throw new IllegalStateException("Replay not initialized");
        }
        if (state == ReplayState.PLAYING) {
            return;
        }
        state = ReplayState.PLAYING;
        scheduleNextStep();
        publishStatus();
    }

    public synchronized void pause() {
        if (state != ReplayState.PLAYING) {
            return;
        }
        state = ReplayState.PAUSED;
        if (playFuture != null) {
            playFuture.cancel(false);
        }
        publishStatus();
    }

    public synchronized void stop() {
        pause();
        state = ReplayState.STOPPED;
        candles = List.of();
        currentIndex = 0;
        currentTimeMs = 0L;
        publishStatus();
    }

    public synchronized boolean step() {
        if (state == ReplayState.STOPPED) {
            throw new IllegalStateException("Replay not initialized");
        }
        if (currentIndex >= candles.size()) {
            stop();
            return false;
        }
        Candle candle = candles.get(currentIndex++);
        currentTimeMs = candle.endTimeMs();
        emitCandle(candle);
        publishStatus();
        return currentIndex < candles.size();
    }

    public synchronized void setSpeed(double multiplier) {
        if (multiplier <= 0.0) {
            throw new IllegalArgumentException("Speed multiplier must be positive");
        }
        this.speedMultiplier = multiplier;
        if (state == ReplayState.PLAYING) {
            if (playFuture != null) {
                playFuture.cancel(false);
            }
            scheduleNextStep();
        }
        publishStatus();
    }

    public synchronized ReplayStatus status() {
        return new ReplayStatus(
                state.name(),
                currentIndex,
                candles.size(),
                speedMultiplier,
                currentTimeMs
        );
    }

    // ── Multi-timeframe aggregation (was MultiTimeframeContext) ─────────

    private static final ThreadLocal<Map<String, Candle>> ACTIVE_5M =
            ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Map<String, Candle>> ACTIVE_15M =
            ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Map<String, Candle>> ACTIVE_DAILY =
            ThreadLocal.withInitial(HashMap::new);

    /**
     * Consumes a 1m candle and returns any higher-timeframe candles that closed as a result.
     * Zero look-ahead: a higher-TF candle is emitted only when its boundary has fully closed.
     */
    public static List<Candle> aggregateHigherTimeframes(Candle oneMinCandle) {
        List<Candle> closed = new ArrayList<>();
        Candle new5m = aggregateInterval(oneMinCandle, ACTIVE_5M.get(), 5, "5m", closed);
        if (new5m != null && !new5m.closed()) {
            // developing; not emitted
        }
        Candle new15m = aggregateInterval(oneMinCandle, ACTIVE_15M.get(), 15, "15m", closed);
        if (new15m != null && !new15m.closed()) {
            // developing; not emitted
        }
        aggregateDaily(oneMinCandle, ACTIVE_DAILY.get(), closed);
        return closed;
    }

    private static Candle aggregateInterval(
            Candle oneMin,
            Map<String, Candle> activeMap,
            int periodMin,
            String intervalName,
            List<Candle> closedList
    ) {
        String symbol = oneMin.symbol();
        long minuteEpoch = oneMin.startTimeMs() / 60_000L;
        long expectedStartMs = (minuteEpoch / periodMin) * periodMin * 60_000L;
        Candle current = activeMap.get(symbol);

        if (current != null && current.startTimeMs() != expectedStartMs) {
            closedList.add(closeCopy(current));
            current = null;
        }

        if (current == null) {
            long expectedEndMs = (expectedStartMs / 60_000L + periodMin) * 60_000L - 1;
            current = new Candle(
                    symbol, intervalName, expectedStartMs, expectedEndMs,
                    oneMin.openPaisa(), oneMin.highPaisa(), oneMin.lowPaisa(), oneMin.closePaisa(),
                    oneMin.volume(), false);
        } else {
            current = new Candle(
                    symbol, intervalName, current.startTimeMs(), current.endTimeMs(),
                    current.openPaisa(),
                    Math.max(current.highPaisa(), oneMin.highPaisa()),
                    Math.min(current.lowPaisa(), oneMin.lowPaisa()),
                    oneMin.closePaisa(),
                    current.volume() + oneMin.volume(),
                    false);
        }
        activeMap.put(symbol, current);
        return current;
    }

    private static void aggregateDaily(
            Candle oneMin,
            Map<String, Candle> activeMap,
            List<Candle> closedList
    ) {
        String symbol = oneMin.symbol();
        LocalDate candleDate = Instant.ofEpochMilli(oneMin.startTimeMs())
                .atZone(CandleBucketPolicy.IST)
                .toLocalDate();
        Candle current = activeMap.get(symbol);
        if (current != null) {
            LocalDate activeDate = Instant.ofEpochMilli(current.startTimeMs())
                    .atZone(CandleBucketPolicy.IST)
                    .toLocalDate();
            if (!activeDate.equals(candleDate)) {
                closedList.add(closeCopy(current));
                current = null;
            }
        }
        if (current == null) {
            long startOfDayMs = candleDate.atStartOfDay(CandleBucketPolicy.IST).toInstant().toEpochMilli();
            long endOfDayMs = candleDate.plusDays(1).atStartOfDay(CandleBucketPolicy.IST).toInstant().toEpochMilli() - 1;
            current = new Candle(
                    symbol, "Daily", startOfDayMs, endOfDayMs,
                    oneMin.openPaisa(), oneMin.highPaisa(), oneMin.lowPaisa(), oneMin.closePaisa(),
                    oneMin.volume(), false);
        } else {
            current = new Candle(
                    symbol, "Daily", current.startTimeMs(), current.endTimeMs(),
                    current.openPaisa(),
                    Math.max(current.highPaisa(), oneMin.highPaisa()),
                    Math.min(current.lowPaisa(), oneMin.lowPaisa()),
                    oneMin.closePaisa(),
                    current.volume() + oneMin.volume(),
                    false);
        }
        activeMap.put(symbol, current);
    }

    private static Candle closeCopy(Candle current) {
        return new Candle(
                current.symbol(), current.interval(), current.startTimeMs(), current.endTimeMs(),
                current.openPaisa(), current.highPaisa(), current.lowPaisa(), current.closePaisa(),
                current.volume(), true);
    }

    private void scheduleNextStep() {
        if (state != ReplayState.PLAYING) {
            return;
        }
        long delayMs = Math.max(1L, (long) (1000.0 / speedMultiplier));
        playFuture = executor.schedule(() -> {
            boolean hasMore;
            synchronized (CandleReplaySession.this) {
                hasMore = step();
                if (hasMore && state == ReplayState.PLAYING) {
                    scheduleNextStep();
                } else if (!hasMore) {
                    state = ReplayState.PAUSED;
                    publishStatus();
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void emitCandle(Candle candle) {
        CandleClosed closed = new CandleClosed(EventMetadata.root(), candle);
        eventBus.ifPresent(bus -> bus.publish(closed));
        gatewayRouter.ifPresent(router -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "REPLAY_CANDLE");
                payload.put("symbol", candle.symbol());
                payload.put("interval", candle.interval());
                payload.put("startTimeMs", candle.startTimeMs());
                payload.put("endTimeMs", candle.endTimeMs());
                payload.put("closePaisa", candle.closePaisa());
                router.publish(GatewayTopic.REPLAY_CONTROL, objectMapper.writeValueAsBytes(payload));
            } catch (Exception e) {
                log.warn("Failed to publish replay candle: {}", e.getMessage());
            }
        });
        ReplayTimeChangedEvent timeEvent = new ReplayTimeChangedEvent(
                EventMetadata.root(),
                currentTimeMs,
                (long) (1_000_000_000L / speedMultiplier)
        );
        eventBus.ifPresent(bus -> bus.publish(timeEvent));
    }

    private void publishStatus() {
        gatewayRouter.ifPresent(router -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                ReplayStatus s = status();
                payload.put("type", "REPLAY_STATUS");
                payload.put("state", s.state());
                payload.put("currentIndex", s.currentIndex());
                payload.put("totalCandles", s.totalCandles());
                payload.put("speedMultiplier", s.speedMultiplier());
                payload.put("currentTimeMs", s.currentTimeMs());
                router.publish(GatewayTopic.REPLAY_CONTROL, objectMapper.writeValueAsBytes(payload));
            } catch (Exception e) {
                log.warn("Failed to publish replay status: {}", e.getMessage());
            }
        });
    }

    public record ReplayStatus(
            String state,
            int currentIndex,
            int totalCandles,
            double speedMultiplier,
            long currentTimeMs
    ) {
    }
}
