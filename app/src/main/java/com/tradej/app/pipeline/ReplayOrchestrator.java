package com.tradej.app.pipeline;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.EventBus;
import com.tradej.persistence.replay.HistoricalRangeService;
import com.tradej.persistence.replay.ReplayResult;
import com.tradej.persistence.replay.ReplayRunner;
import com.tradej.pipeline.clock.EventTimestamps;
import com.tradej.pipeline.clock.VirtualClock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Wraps Chronicle and DuckDB historical replay with virtual clock mode transitions
 * for deterministic pipeline execution.
 */
@Service
public final class ReplayOrchestrator {

    private final ReplayRunner replayRunner;
    private final HistoricalRangeService historicalRangeService;
    private final VirtualClock virtualClock;

    public ReplayOrchestrator(
            ReplayRunner replayRunner,
            @Qualifier("localHistoricalRangeService") HistoricalRangeService historicalRangeService,
            VirtualClock virtualClock
    ) {
        this.replayRunner = replayRunner;
        this.historicalRangeService = historicalRangeService;
        this.virtualClock = virtualClock;
    }

    public ReplayResult replayChronicle(Class<? extends DomainEvent> eventType) {
        return withReplayMode(() -> replayRunner.replayAll(eventType));
    }

    public ReplayResult replayTicks(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return withReplayMode(() -> historicalRangeService.replayTicks(symbol, fromMs, toMs, clockSyncedBus(eventBus)));
    }

    public ReplayResult replayCandles(String symbol, String interval, long fromMs, long toMs, EventBus eventBus) {
        return withReplayMode(() -> historicalRangeService.replayCandles(symbol, interval, fromMs, toMs, clockSyncedBus(eventBus)));
    }

    public ReplayResult replayFillEvents(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return withReplayMode(() -> historicalRangeService.replayFillEvents(symbol, fromMs, toMs, clockSyncedBus(eventBus)));
    }

    /**
     * Replay historical trade lifecycle events (TradeOpened/TradeClosed) through the event bus.
     * Used at startup to rebuild portfolio, risk, and position state from DuckDB persistence.
     */
    public ReplayResult replayTradeLifecycle(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return withReplayMode(() -> historicalRangeService.replayTradeLifecycle(symbol, fromMs, toMs, clockSyncedBus(eventBus)));
    }

    /**
     * Replay all historical trade lifecycle events (no filter) — shortcut for startup rebuild.
     */
    public ReplayResult replayTradeLifecycle(EventBus eventBus) {
        return replayTradeLifecycle(null, 0L, Long.MAX_VALUE, eventBus);
    }

    public ReplayResult replayOrders(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return withReplayMode(() -> historicalRangeService.replayOrders(symbol, fromMs, toMs, clockSyncedBus(eventBus)));
    }

    private ReplayResult withReplayMode(ReplayAction action) {
        virtualClock.enterReplayMode();
        try {
            return action.run();
        } finally {
            virtualClock.enterLiveMode();
        }
    }

    private EventBus clockSyncedBus(EventBus delegate) {
        return new EventBus() {
            @Override
            public <T extends DomainEvent> void subscribe(Class<T> eventType, com.tradej.core.domain.port.DomainEventHandler<T> handler) {
                delegate.subscribe(eventType, handler);
            }

            @Override
            public <T extends DomainEvent> void unsubscribe(Class<T> eventType, com.tradej.core.domain.port.DomainEventHandler<T> handler) {
                delegate.unsubscribe(eventType, handler);
            }

            @Override
            public void publish(DomainEvent event) {
                if (virtualClock.getMode() == VirtualClock.Mode.REPLAY) {
                    virtualClock.advanceVirtualTimeMs(EventTimestamps.exchangeOrEventTimeMs(event));
                }
                delegate.publish(event);
            }

            @Override
            public void start() {
                delegate.start();
            }

            @Override
            public void stop() {
                delegate.stop();
            }
        };
    }

    @FunctionalInterface
    private interface ReplayAction {
        ReplayResult run();
    }
}
