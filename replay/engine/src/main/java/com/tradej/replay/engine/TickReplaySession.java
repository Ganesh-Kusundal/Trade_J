package com.tradej.replay.engine;

import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Tick-level replay session for high-fidelity market data replay.
 * Replays individual tick events at configurable speed.
 */
public final class TickReplaySession {

    public enum ReplayState { STOPPED, PLAYING, PAUSED }

    private final Optional<EventBus> eventBus;
    private final EventMetadataFactory metadataFactory;
    private final ScheduledExecutorService executor;

    private List<TickRecord> ticks = List.of();
    private int currentIndex;
    private ReplayState state = ReplayState.STOPPED;
    private double speedMultiplier = 1.0;
    private ScheduledFuture<?> playFuture;
    private Consumer<MarketTickEvent> tickListener;

    public TickReplaySession(Optional<EventBus> eventBus, EventMetadataFactory metadataFactory) {
        this.eventBus = eventBus;
        this.metadataFactory = metadataFactory;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tick-replay");
            t.setDaemon(true);
            return t;
        });
    }

    public void load(List<TickRecord> ticks) {
        this.ticks = List.copyOf(ticks);
        this.currentIndex = 0;
        this.state = ReplayState.STOPPED;
    }

    public void play() {
        if (state == ReplayState.PLAYING || ticks.isEmpty()) return;
        state = ReplayState.PLAYING;
        scheduleNext();
    }

    public void pause() {
        if (playFuture != null) playFuture.cancel(false);
        state = ReplayState.PAUSED;
    }

    public void stop() {
        if (playFuture != null) playFuture.cancel(false);
        state = ReplayState.STOPPED;
        currentIndex = 0;
    }

    public void step() {
        if (currentIndex < ticks.size()) {
            emitTick(ticks.get(currentIndex));
            currentIndex++;
        }
    }

    public void setSpeed(double multiplier) {
        this.speedMultiplier = Math.max(0.1, Math.min(multiplier, 100.0));
    }

    public void onTick(Consumer<MarketTickEvent> listener) {
        this.tickListener = listener;
    }

    public ReplayState state() { return state; }
    public int currentIndex() { return currentIndex; }
    public int totalTicks() { return ticks.size(); }
    public double speed() { return speedMultiplier; }

    private void scheduleNext() {
        if (currentIndex >= ticks.size()) {
            state = ReplayState.STOPPED;
            return;
        }
        TickRecord tick = ticks.get(currentIndex);
        long delayMs = 0;
        if (currentIndex > 0) {
            long gap = tick.timestampMs() - ticks.get(currentIndex - 1).timestampMs();
            delayMs = Math.max(0, (long) (gap / speedMultiplier));
        }
        playFuture = executor.schedule(() -> {
            if (state != ReplayState.PLAYING) return;
            emitTick(tick);
            currentIndex++;
            scheduleNext();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void emitTick(TickRecord record) {
        MarketTickEvent event = new MarketTickEvent(
                metadataFactory.root(), 1L,
                record.symbol(), record.segment(), FeedMode.FULL,
                record.ltpPaisa(), record.bidPaisa(), record.askPaisa(),
                record.timestampMs(), Optional.empty(), 0L, 0L);
        eventBus.ifPresent(bus -> bus.publish(event));
        if (tickListener != null) tickListener.accept(event);
    }

    public void close() {
        stop();
        executor.shutdown();
    }

    public record TickRecord(
            String symbol,
            ExchangeSegment segment,
            long ltpPaisa,
            long bidPaisa,
            long askPaisa,
            long timestampMs
    ) {}
}
