package com.tradej.disruptor;

import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.disruptor.config.DisruptorPipelineConfig;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.pipeline.runtime.PipelineRuntimeBridge;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import com.tradej.strategy.service.StrategyEngine;

/**
 * Bridge for deprecated {@link DisruptorEventBus} constructors.
 * Converts legacy constructor parameter lists into {@link DisruptorPipelineConfig}.
 */
final class DisruptorEventBusLegacySupport {

    private DisruptorEventBusLegacySupport() {
    }

    static DisruptorPipelineConfig toConfig(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue,
            PipelineRuntimeBridge pipelineRuntimeBridge,
            boolean compileGraphOnInit
    ) {
        return new DisruptorPipelineConfig(
                positionRiskHandler,
                candleAggregationService,
                strategyEngine,
                graphStrategySandbox,
                executionHandler,
                portfolioEngine,
                stageTimings != null ? stageTimings : StageTimings.NO_OP,
                hotPathFeatureStore,
                deadLetterQueue != null ? deadLetterQueue : DeadLetterQueue.noop(),
                pipelineRuntimeBridge,
                compileGraphOnInit
        );
    }
}
