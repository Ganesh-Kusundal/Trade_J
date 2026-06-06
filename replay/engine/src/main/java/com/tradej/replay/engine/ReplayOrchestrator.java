package com.tradej.replay.engine;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.EventBus;
import com.tradej.persistence.replay.HistoricalRangeService;
import com.tradej.persistence.replay.ReplayResult;
import com.tradej.persistence.replay.ReplayRunner;
import com.tradej.persistence.replay.ReplayStateManager;
import com.tradej.pipeline.clock.EventTimestamps;
import com.tradej.pipeline.clock.VirtualClock;

public final class ReplayOrchestrator {

    private final ReplayRunner replayRunner;
    private final HistoricalRangeService historicalRangeService;
    private final VirtualClock virtualClock;
    private final ReplayStateManager replayStateManager;

    public ReplayOrchestrator(
            ReplayRunner replayRunner,
            HistoricalRangeService historicalRangeService,
            VirtualClock virtualClock,
            ReplayStateManager replayStateManager
    ) {
        this.replayRunner = replayRunner;
        this.historicalRangeService = historicalRangeService;
        this.virtualClock = virtualClock;
        this.replayStateManager = replayStateManager;
    }

    public ReplayResult replayChronicle(Class<? extends DomainEvent> eventType) {
        return withReplayMode(() -> replayRunner.replayAll(eventType));
    }

    public ReplayResult replayTicks(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return replayTicks(symbol, fromMs, toMs, eventBus, 0, 50_000);
    }

    public ReplayResult replayTicks(
            String symbol,
            long fromMs,
            long toMs,
            EventBus eventBus,
            int offset,
            int batchSize
    ) {
        return withReplayMode(() -> historicalRangeService.replayMarketTicks(
                symbol, fromMs, toMs, clockSyncedBus(eventBus), offset, batchSize));
    }

    public ReplayResult replayCandles(String symbol, String interval, long fromMs, long toMs, EventBus eventBus) {
        return withReplayMode(() -> historicalRangeService.replayCandles(symbol, interval, fromMs, toMs, clockSyncedBus(eventBus)));
    }

    public ReplayResult replayFillEvents(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return withReplayMode(() -> historicalRangeService.replayFillEvents(symbol, fromMs, toMs, clockSyncedBus(eventBus)));
    }

    public ReplayResult replayTradeLifecycle(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return withReplayMode(() -> historicalRangeService.replayTradeLifecycle(symbol, fromMs, toMs, clockSyncedBus(eventBus)));
    }

    public ReplayResult replayTradeLifecycle(EventBus eventBus) {
        return replayTradeLifecycle(null, 0L, Long.MAX_VALUE, eventBus);
    }

    public ReplayResult replayTradeLifecycleStartup(EventBus eventBus) {
        return withClockModeOnly(() -> historicalRangeService.replayTradeLifecycle(null, 0L, Long.MAX_VALUE, clockSyncedBus(eventBus)));
    }

    public ReplayResult replayOrders(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return withReplayMode(() -> historicalRangeService.replayOrders(symbol, fromMs, toMs, clockSyncedBus(eventBus)));
    }

    private ReplayResult withReplayMode(ReplayAction action) {
        virtualClock.enterReplayMode();
        replayStateManager.beforeReplay();
        try {
            return action.run();
        } finally {
            replayStateManager.afterReplay();
            virtualClock.enterLiveMode();
        }
    }

    private ReplayResult withClockModeOnly(ReplayAction action) {
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
