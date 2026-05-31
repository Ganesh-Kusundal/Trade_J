package com.tradej.app.pipeline;

import com.tradej.core.domain.port.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Rebuilds portfolio, risk, and position state from DuckDB event store at startup.
 *
 * <p>Queries the {@code trade_lifecycle} table for all historical {@link com.tradej.core.domain.event.TradeOpened}
 * and {@link com.tradej.core.domain.event.TradeClosed} events, then publishes them through the
 * event bus in chronological order. Subscribers ({@link com.tradej.execution.position.EventSourcedNetPositionProvider},
 * {@link com.tradej.strategy.portfolio.PortfolioEngine}, {@link com.tradej.execution.risk.PositionRiskHandler},
 * {@link com.tradej.app.readmodel.ReadModelStore}) automatically rebuild their state from the
 * replayed events.
 *
 * <p>Called during {@code StartupConfiguration} after event subscriptions are wired but before
 * the live broker connection is established.
 */
@Service
public final class PositionStateRebuilder {

    private static final Logger log = LoggerFactory.getLogger(PositionStateRebuilder.class);

    private final ReplayOrchestrator replayOrchestrator;

    public PositionStateRebuilder(ReplayOrchestrator replayOrchestrator) {
        this.replayOrchestrator = replayOrchestrator;
    }

    /**
     * Rebuild position state from all historical trade lifecycle events in DuckDB.
     *
     * <p>The event bus must be started before calling this method so that events can
     * be published to registered subscribers.
     *
     * @param eventBus the running event bus to publish events into
     */
    public void rebuild(EventBus eventBus) {
        log.info("Rebuilding position state from historical trade lifecycle events...");
        // Use startup variant that does NOT snapshot/restore — we WANT to populate state
        var result = replayOrchestrator.replayTradeLifecycleStartup(eventBus);
        if (result.totalRead() == 0L) {
            log.info("No historical trade lifecycle events found — position state is empty");
        } else {
            log.info("Position state rebuilt: {} events replayed, {} failed, {} total",
                    result.replayed(), result.failed(), result.totalRead());
        }
    }
}
