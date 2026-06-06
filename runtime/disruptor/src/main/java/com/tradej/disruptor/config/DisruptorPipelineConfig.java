package com.tradej.disruptor.config;

import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.disruptor.DisruptorEventBus;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.pipeline.runtime.PipelineRuntimeBridge;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import com.tradej.strategy.service.StrategyEngine;

/**
 * Immutable configuration record for creating a {@link DisruptorEventBus}.
 *
 * <p>Use {@link DisruptorPipelineBuilder} to construct instances with
 * sensible defaults for optional parameters.
 */
public record DisruptorPipelineConfig(
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
    public DisruptorPipelineConfig {
        if (positionRiskHandler == null) {
            throw new IllegalArgumentException("positionRiskHandler must not be null");
        }
        if (strategyEngine == null) {
            throw new IllegalArgumentException("strategyEngine must not be null");
        }
        if (executionHandler == null) {
            throw new IllegalArgumentException("executionHandler must not be null");
        }
        if (pipelineRuntimeBridge == null) {
            throw new IllegalArgumentException("pipelineRuntimeBridge must not be null");
        }
        if (stageTimings == null) stageTimings = StageTimings.NO_OP;
        if (deadLetterQueue == null) deadLetterQueue = DeadLetterQueue.noop();
    }
}
