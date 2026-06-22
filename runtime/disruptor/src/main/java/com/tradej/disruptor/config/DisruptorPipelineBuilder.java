package com.tradej.disruptor.config;

import com.tradej.core.domain.config.TradeDefaults;
import com.tradej.core.domain.event.EventDeliveryPolicy;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.disruptor.DisruptorEventBus;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.pipeline.runtime.PipelineRuntimeBridge;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;

/**
 * Fluent builder for {@link DisruptorPipelineConfig} and {@link DisruptorEventBus}.
 *
 * <p>Required parameters:
 * <ul>
 *   <li>{@link #positionRiskHandler(PositionRiskHandler)}</li>
 *   <li>{@link #executionHandler(ExecutionHandler)}</li>
 *   <li>{@link #pipelineRuntimeBridge(PipelineRuntimeBridge)}</li>
 * </ul>
 *
 * <p>All other parameters have sensible defaults.
 */
public final class DisruptorPipelineBuilder {

    private PositionRiskHandler positionRiskHandler;
    private CandleAggregationService candleAggregationService;
    private GraphStrategySandbox graphStrategySandbox;
    private ExecutionHandler executionHandler;
    private PortfolioEngine portfolioEngine;
    private StageTimings stageTimings = StageTimings.NO_OP;
    private FeatureStore hotPathFeatureStore;
    private DeadLetterQueue deadLetterQueue = DeadLetterQueue.noop();
    private PipelineRuntimeBridge pipelineRuntimeBridge;
    private boolean compileGraphOnInit = true;
    private com.tradej.core.domain.runtime.RuntimeMode runtimeMode = TradeDefaults.RUNTIME_MODE;
    private com.tradej.core.domain.port.EventWriteAheadLog writeAheadLog = com.tradej.core.domain.port.EventWriteAheadLog.noop();
    private EventDeliveryPolicy deliveryPolicy = EventDeliveryPolicy.defaults();

    public DisruptorPipelineBuilder positionRiskHandler(PositionRiskHandler positionRiskHandler) {
        this.positionRiskHandler = positionRiskHandler;
        return this;
    }

    public DisruptorPipelineBuilder candleAggregationService(CandleAggregationService candleAggregationService) {
        this.candleAggregationService = candleAggregationService;
        return this;
    }

    public DisruptorPipelineBuilder graphStrategySandbox(GraphStrategySandbox graphStrategySandbox) {
        this.graphStrategySandbox = graphStrategySandbox;
        return this;
    }

    public DisruptorPipelineBuilder executionHandler(ExecutionHandler executionHandler) {
        this.executionHandler = executionHandler;
        return this;
    }

    public DisruptorPipelineBuilder portfolioEngine(PortfolioEngine portfolioEngine) {
        this.portfolioEngine = portfolioEngine;
        return this;
    }

    public DisruptorPipelineBuilder stageTimings(StageTimings stageTimings) {
        this.stageTimings = stageTimings;
        return this;
    }

    public DisruptorPipelineBuilder hotPathFeatureStore(FeatureStore hotPathFeatureStore) {
        this.hotPathFeatureStore = hotPathFeatureStore;
        return this;
    }

    public DisruptorPipelineBuilder deadLetterQueue(DeadLetterQueue deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
        return this;
    }

    public DisruptorPipelineBuilder pipelineRuntimeBridge(PipelineRuntimeBridge pipelineRuntimeBridge) {
        this.pipelineRuntimeBridge = pipelineRuntimeBridge;
        return this;
    }

    public DisruptorPipelineBuilder compileGraphOnInit(boolean compileGraphOnInit) {
        this.compileGraphOnInit = compileGraphOnInit;
        return this;
    }

    public DisruptorPipelineBuilder runtimeMode(com.tradej.core.domain.runtime.RuntimeMode runtimeMode) {
        this.runtimeMode = runtimeMode;
        return this;
    }

    public DisruptorPipelineBuilder writeAheadLog(com.tradej.core.domain.port.EventWriteAheadLog writeAheadLog) {
        this.writeAheadLog = writeAheadLog;
        return this;
    }

    public DisruptorPipelineBuilder deliveryPolicy(EventDeliveryPolicy deliveryPolicy) {
        this.deliveryPolicy = deliveryPolicy;
        return this;
    }

    public DisruptorPipelineConfig build() {
        return new DisruptorPipelineConfig(
                positionRiskHandler,
                candleAggregationService,
                graphStrategySandbox,
                executionHandler,
                portfolioEngine,
                stageTimings,
                hotPathFeatureStore,
                deadLetterQueue,
                pipelineRuntimeBridge,
                compileGraphOnInit,
                runtimeMode,
                writeAheadLog,
                deliveryPolicy
        );
    }

    public DisruptorEventBus buildBus() {
        return new DisruptorEventBus(build());
    }
}
