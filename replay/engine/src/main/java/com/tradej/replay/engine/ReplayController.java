package com.tradej.replay.engine;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.time.ReplayTradingClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controller to handle play, pause, stepping, and speed adjustments of historical candles
 * and time aggregates in the replay sandbox.
 */
public class ReplayController {
    private static final Logger log = LoggerFactory.getLogger(ReplayController.class);

    public enum ReplayState {
        STOPPED,
        PLAYING,
        PAUSED
    }

    private final EventBus eventBus;
    private final MultiTimeframeContext timeframeContext;
    private final ScheduledExecutorService executorService;

    private List<Candle> candles = Collections.synchronizedList(new ArrayList<>());
    private int currentIndex = 0;
    private ReplayState state = ReplayState.STOPPED;
    private ReplayTradingClock clock;
    private double speedMultiplier = 1.0; // 1 second of real-time = 1 second of replay-time
    private ScheduledFuture<?> playFuture;

    public ReplayController(EventBus eventBus) {
        this.eventBus = eventBus;
        this.timeframeContext = new MultiTimeframeContext();
        this.executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "replay-loop-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Initializes a new replay session with historical 1m candles.
     */
    public synchronized void start(List<Candle> replayCandles) {
        stop();
        if (replayCandles == null || replayCandles.isEmpty()) {
            log.warn("Attempted to start replay with empty candle list");
            return;
        }

        this.candles = new ArrayList<>(replayCandles);
        // Sort ascending by time
        this.candles.sort((c1, c2) -> Long.compare(c1.startTimeMs(), c2.startTimeMs()));
        this.currentIndex = 0;

        // Initialize clock with the start of the first candle
        this.clock = new ReplayTradingClock(Instant.ofEpochMilli(candles.get(0).startTimeMs()));
        this.state = ReplayState.PAUSED;
        log.info("Replay session initialized with {} candles. Start time={}", candles.size(), clock.instant());
    }

    /**
     * Plays the replay at the current speed multiplier.
     */
    public synchronized void play() {
        if (state == ReplayState.STOPPED) {
            log.warn("Cannot play: Replay is not initialized");
            return;
        }
        if (state == ReplayState.PLAYING) {
            return;
        }

        state = ReplayState.PLAYING;
        scheduleNextStep();
        log.info("Replay started playing (speed={}x)", speedMultiplier);
    }

    /**
     * Pauses replay.
     */
    public synchronized void pause() {
        if (state != ReplayState.PLAYING) {
            return;
        }
        state = ReplayState.PAUSED;
        if (playFuture != null) {
            playFuture.cancel(false);
        }
        log.info("Replay paused at index={}/{}", currentIndex, candles.size());
    }

    /**
     * Stops and resets replay.
     */
    public synchronized void stop() {
        pause();
        this.state = ReplayState.STOPPED;
        this.candles.clear();
        this.currentIndex = 0;
        this.clock = null;
        log.info("Replay session stopped and cleared.");
    }

    /**
     * Steps forward by a single 1m candle.
     */
    public synchronized boolean step() {
        if (state == ReplayState.STOPPED) {
            log.warn("Cannot step: Replay is stopped");
            return false;
        }
        if (currentIndex >= candles.size()) {
            log.info("Replay reached the end of available candle dataset");
            stop();
            return false;
        }

        Candle candle1m = candles.get(currentIndex++);
        
        // 1. Advance replay clock
        clock.advanceTo(Instant.ofEpochMilli(candle1m.endTimeMs()));

        // 2. Publish original 1m closed event
        CandleClosed closed1mEvent = new CandleClosed(EventMetadata.root(), candle1m);
        eventBus.publish(closed1mEvent);

        // 3. Process MultiTimeframe Context and emit completed higher bars
        MultiTimeframeContext.TimeframeResult result = timeframeContext.process1mCandle(candle1m);
        for (Candle higherBar : result.closedCandles()) {
            eventBus.publish(new CandleClosed(EventMetadata.root(), higherBar));
        }

        // 4. Publish ReplayTimeChangedEvent
        ReplayTimeChangedEvent timeEvent = new ReplayTimeChangedEvent(
            EventMetadata.root(),
            clock.millis(),
            (long) (1000_000_000L / speedMultiplier)
        );
        eventBus.publish(timeEvent);

        return true;
    }

    /**
     * Adjusts the playback speed.
     */
    public synchronized void setSpeed(double multiplier) {
        if (multiplier <= 0.0) {
            throw new IllegalArgumentException("Replay speed multiplier must be positive");
        }
        this.speedMultiplier = multiplier;
        if (state == ReplayState.PLAYING) {
            // Re-schedule with new delay
            if (playFuture != null) {
                playFuture.cancel(false);
            }
            scheduleNextStep();
        }
        log.info("Replay speed adjusted to {}x", multiplier);
    }

    private void scheduleNextStep() {
        if (state != ReplayState.PLAYING) {
            return;
        }

        // Delay in ms to simulate real-time playback: 60s / speedMultiplier
        long delayMs = (long) (1000.0 / speedMultiplier);
        if (delayMs < 1) delayMs = 1; // Cap at 1ms max frequency

        playFuture = executorService.schedule(() -> {
            boolean hasMore;
            synchronized (this) {
                hasMore = step();
                if (hasMore && state == ReplayState.PLAYING) {
                    scheduleNextStep();
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public synchronized ReplayState getState() {
        return state;
    }

    public synchronized ReplayTradingClock getClock() {
        return clock;
    }

    public synchronized double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public synchronized int getCurrentIndex() {
        return currentIndex;
    }

    public synchronized int getTotalCandles() {
        return candles.size();
    }
}
