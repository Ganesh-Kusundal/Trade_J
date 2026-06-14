package com.tradej.replay.engine;

import com.tradej.core.domain.service.PositionService;
import com.tradej.execution.readmodel.ReadModelStore;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.persistence.replay.ReplayStateManager;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class IsolatedReplayStateManager implements ReplayStateManager {

    private static final Logger log = LoggerFactory.getLogger(IsolatedReplayStateManager.class);

    private final PortfolioEngine portfolioEngine;
    // P3.4: retyped from EventSourcedNetPositionProvider to the canonical
    // PositionService (which has snapshot()/restore() for replay isolation).
    // P3.6: the legacy class has been removed; PositionService is the sole
    // NetPositionProvider subtype. Caller (DataConfiguration) passes
    // FullComposition.executionComposition().positionService().
    private final PositionService positionService;
    private final PositionRiskHandler positionRiskHandler;
    private final CandleAggregationService candleAggregationService;
    private final ReadModelStore readModelStore;
    private final OrderManagementService orderManagementService;

    private PortfolioEngine.StateSnapshot portfolioSnapshot;
    private PositionService.StateSnapshot positionSnapshot;
    private PositionRiskHandler.StateSnapshot riskSnapshot;
    private CandleAggregationService.StateSnapshot candleSnapshot;
    private ReadModelStore.ReplaySnapshot readModelSnapshot;
    private OrderManagementService.StateSnapshot orderSnapshot;

    public IsolatedReplayStateManager(
            PortfolioEngine portfolioEngine,
            PositionService positionService,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            ReadModelStore readModelStore,
            OrderManagementService orderManagementService
    ) {
        this.portfolioEngine = Objects.requireNonNull(portfolioEngine, "portfolioEngine");
        this.positionService = Objects.requireNonNull(positionService, "positionService");
        this.positionRiskHandler = Objects.requireNonNull(positionRiskHandler, "positionRiskHandler");
        this.candleAggregationService = Objects.requireNonNull(candleAggregationService, "candleAggregationService");
        this.readModelStore = Objects.requireNonNull(readModelStore, "readModelStore");
        this.orderManagementService = Objects.requireNonNull(orderManagementService, "orderManagementService");
    }

    @Override
    public void beforeReplay() {
        log.info("Snapshotting pipeline state for replay isolation...");
        portfolioSnapshot = portfolioEngine.snapshot();
        positionSnapshot = positionService.snapshot();
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
        if (positionSnapshot != null) {
            positionService.restore(positionSnapshot);
            positionSnapshot = null;
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
