package com.tradej.app.pipeline;

import com.tradej.app.readmodel.ReadModelStore;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.persistence.replay.ReplayStateManager;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * {@link ReplayStateManager} that snapshots all mutable pipeline state before replay
 * and restores it after replay, preventing replay events from corrupting live state (AD-02).
 *
 * <p>Snapshots the following components:
 * <ul>
 *   <li>{@link PortfolioEngine} — capital allocations, net positions, signal attribution</li>
 *   <li>{@link EventSourcedNetPositionProvider} — net position tracking per symbol</li>
 *   <li>{@link PositionRiskHandler} — open trades, kill switch, loss counters</li>
 *   <li>{@link CandleAggregationService} — open candle buckets</li>
 *   <li>{@link ReadModelStore} — UI-facing read model projections</li>
 * </ul>
 *
 * <p>Snapshots are taken in {@link #beforeReplay()} and restored in {@link #afterReplay()}.
 * This ensures that replay events can flow through the pipeline for validation/verification
 * without corrupting the live state on restore.
 */
public final class IsolatedReplayStateManager implements ReplayStateManager {

    private static final Logger log = LoggerFactory.getLogger(IsolatedReplayStateManager.class);

    private final PortfolioEngine portfolioEngine;
    private final EventSourcedNetPositionProvider netPositionProvider;
    private final PositionRiskHandler positionRiskHandler;
    private final CandleAggregationService candleAggregationService;
    private final ReadModelStore readModelStore;
    private final OrderManagementService orderManagementService;

    // Captured snapshots
    private PortfolioEngine.StateSnapshot portfolioSnapshot;
    private EventSourcedNetPositionProvider.StateSnapshot netPositionSnapshot;
    private PositionRiskHandler.StateSnapshot riskSnapshot;
    private CandleAggregationService.StateSnapshot candleSnapshot;
    private ReadModelStore.ReplaySnapshot readModelSnapshot;
    private OrderManagementService.StateSnapshot orderSnapshot;

    public IsolatedReplayStateManager(
            PortfolioEngine portfolioEngine,
            EventSourcedNetPositionProvider netPositionProvider,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            ReadModelStore readModelStore,
            OrderManagementService orderManagementService
    ) {
        this.portfolioEngine = Objects.requireNonNull(portfolioEngine, "portfolioEngine");
        this.netPositionProvider = Objects.requireNonNull(netPositionProvider, "netPositionProvider");
        this.positionRiskHandler = Objects.requireNonNull(positionRiskHandler, "positionRiskHandler");
        this.candleAggregationService = Objects.requireNonNull(candleAggregationService, "candleAggregationService");
        this.readModelStore = Objects.requireNonNull(readModelStore, "readModelStore");
        this.orderManagementService = Objects.requireNonNull(orderManagementService, "orderManagementService");
    }

    @Override
    public void beforeReplay() {
        log.info("Snapshotting pipeline state for replay isolation...");
        portfolioSnapshot = portfolioEngine.snapshot();
        netPositionSnapshot = netPositionProvider.snapshot();
        riskSnapshot = positionRiskHandler.snapshot();
        candleSnapshot = candleAggregationService.snapshot();
        readModelSnapshot = readModelStore.replaySnapshot();
        orderSnapshot = orderManagementService.snapshot();
        log.info("Pipeline state snapshotted — replay can proceed safely");
    }

    @Override
    public void afterReplay() {
        log.info("Restoring pipeline state from pre-replay snapshot...");
        if (portfolioSnapshot != null) {
            portfolioEngine.restore(portfolioSnapshot);
            portfolioSnapshot = null;
        }
        if (netPositionSnapshot != null) {
            netPositionProvider.restore(netPositionSnapshot);
            netPositionSnapshot = null;
        }
        if (riskSnapshot != null) {
            positionRiskHandler.restore(riskSnapshot);
            riskSnapshot = null;
        }
        if (candleSnapshot != null) {
            candleAggregationService.restore(candleSnapshot);
            candleSnapshot = null;
        }
        if (readModelSnapshot != null) {
            readModelStore.restore(readModelSnapshot);
            readModelSnapshot = null;
        }
        if (orderSnapshot != null) {
            orderManagementService.restore(orderSnapshot);
            orderSnapshot = null;
        }
        log.info("Pipeline state restored after replay — AD-02 isolation complete");
    }
}
