package com.tradej.app.pipeline;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.EventBus;
import com.tradej.persistence.replay.HistoricalRangeService;
import com.tradej.persistence.replay.ReplayResult;
import com.tradej.persistence.replay.ReplayRunner;
import com.tradej.persistence.replay.ReplayStateManager;
import com.tradej.pipeline.clock.EventTimestamps;
import com.tradej.pipeline.clock.VirtualClock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Wraps Chronicle and DuckDB historical replay with virtual clock mode transitions
 * and pipeline state isolation for deterministic execution (AD-02 fix).
 *
 * <p>All replay paths are wrapped in {@link #withReplayMode} which:
 * <ol>
 *   <li>Snapshots pipeline state via {@link ReplayStateManager#beforeReplay()}</li>
 *   <li>Enters virtual clock replay mode</li>
 *   <li>Executes the replay action</li>
 *   <li>Restores pipeline state via {@link ReplayStateManager#afterReplay()}</li>
 *   <li>Returns to live clock mode</li>
 * </ol>
 */
@Service
public final class ReplayOrchestrator {

    private final ReplayRunner replayRunner;
    private final HistoricalRangeService historicalRangeService;
    private final VirtualClock virtualClock;
    private final ReplayStateManager replayStateManager;

    public ReplayOrchestrator(
            ReplayRunner replayRunner,
            @Qualifier("localHistoricalRangeService") HistoricalRangeService historicalRangeService,
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

    /**
     * Startup-only variant of {@link #replayTradeLifecycle} that replays trade lifecycle events
     * {@code without} state isolation (snapshot/restore).
     *
     * <p>At startup, the pipeline state is empty and we WANT to populate it from historical events.
     * Using the standard {@link #withReplayMode} path would snapshot the empty state, replay events
     * to populate it, then restore back to empty — wiping the rebuild. This method skips the
     * {@link ReplayStateManager} calls so that state is preserved after replay (AD-02 exception).
     *
     * <p>Only safe at startup when no live events are flowing. Do NOT use this for runtime replays.
     */
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

    /**
     * Clock-only variant: enters replay mode, runs the action, exits replay mode.
     * Skips state isolation (no snapshot/restore). Used exclusively by startup rebuild.
     */
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
