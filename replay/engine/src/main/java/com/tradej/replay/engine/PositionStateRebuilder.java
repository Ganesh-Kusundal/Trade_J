package com.tradej.replay.engine;

import com.tradej.core.domain.port.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PositionStateRebuilder {

    private static final Logger log = LoggerFactory.getLogger(PositionStateRebuilder.class);

    private final ReplayOrchestrator replayOrchestrator;

    public PositionStateRebuilder(ReplayOrchestrator replayOrchestrator) {
        this.replayOrchestrator = replayOrchestrator;
    }

    public void rebuild(EventBus eventBus) {
        log.info("Rebuilding position state from historical trade lifecycle events...");
        var result = replayOrchestrator.replayTradeLifecycleStartup(eventBus);
        if (result.totalRead() == 0L) {
            log.info("No historical trade lifecycle events found — position state is empty");
        } else {
            log.info("Position state rebuilt: {} events replayed, {} failed, {} total",
                    result.replayed(), result.failed(), result.totalRead());
        }
    }
}
