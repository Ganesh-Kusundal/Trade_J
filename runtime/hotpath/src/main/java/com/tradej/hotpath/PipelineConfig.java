package com.tradej.hotpath;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.disruptor.DisruptorEventBus;
import com.tradej.disruptor.ShardedDisruptorEventBus;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import com.tradej.strategy.service.StrategyEngine;
import com.tradej.pipeline.runtime.PipelineRuntimeBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Pure Java wiring factory for the hot-path event pipeline.
 *
 * <p>This class contains zero Spring annotations. It creates and wires
 * together the {@link DisruptorEventBus}, {@link MarketDataPipeline}, and
 * {@link OrderPipeline} from their domain dependencies. The resulting
 * {@link PipelineComponents} record is consumed by the Spring
 * configuration layer ({@code EventBusConfiguration}) which delegates
 * to this factory.
 *
 * <p>By keeping all wiring logic here, the hot path can be assembled,
 * configured, and tested without a Spring container.
 */
public final class PipelineConfig {

    private static final Logger log = LoggerFactory.getLogger(PipelineConfig.class);

    private PipelineConfig() {
        // factory class
    }

    /**
     * Create and wire the hot-path pipeline components.
     *
     * @param positionRiskHandler      risk qualification handler
     * @param candleAggregationService candle aggregation service (ticks → candles)
     * @param strategyEngine           strategy evaluation engine
     * @param executionHandler         order execution handler
     * @param portfolioEngine          portfolio engine for capital/exposure checks (nullable)
     * @return a record containing all assembled pipeline components
     */
    public static PipelineComponents create(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine
    ) {
        return create(1, positionRiskHandler, candleAggregationService, strategyEngine, null,
                executionHandler, portfolioEngine, StageTimings.NO_OP, null, DeadLetterQueue.noop(), null);
    }

    /**
     * Create and wire the hot-path pipeline components with per-stage latency timing.
     *
     * @param positionRiskHandler      risk qualification handler
     * @param candleAggregationService candle aggregation service (ticks → candles)
     * @param strategyEngine           strategy evaluation engine
     * @param executionHandler         order execution handler
     * @param portfolioEngine          portfolio engine for capital/exposure checks (nullable)
     * @param stageTimings             per-stage latency callbacks (use {@link StageTimings#NO_OP} to disable)
     * @return a record containing all assembled pipeline components
     */
    public static PipelineComponents create(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings
    ) {
        return create(1, positionRiskHandler, candleAggregationService, strategyEngine, null,
                executionHandler, portfolioEngine, stageTimings, null, DeadLetterQueue.noop(), null);
    }

    /**
     * Create and wire the hot-path pipeline components with graph strategy sandbox.
     *
     * @param positionRiskHandler      risk qualification handler
     * @param candleAggregationService candle aggregation service (ticks → candles)
     * @param strategyEngine           legacy candle-only strategy evaluation engine
     * @param graphStrategySandbox     graph strategy sandbox for tick/depth/multi-event plugins (nullable)
     * @param executionHandler         order execution handler
     * @param portfolioEngine          portfolio engine for capital/exposure checks (nullable)
     * @param stageTimings             per-stage latency callbacks
     * @return a record containing all assembled pipeline components
     */
    public static PipelineComponents create(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings
    ) {
        return create(1, positionRiskHandler, candleAggregationService, strategyEngine, graphStrategySandbox,
                executionHandler, portfolioEngine, stageTimings, null, DeadLetterQueue.noop(), null);
    }

    public static PipelineComponents create(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue
    ) {
        return create(1, positionRiskHandler, candleAggregationService, strategyEngine, null,
                executionHandler, portfolioEngine, stageTimings, hotPathFeatureStore, deadLetterQueue, null);
    }

    public static PipelineComponents create(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue
    ) {
        return create(1, positionRiskHandler, candleAggregationService, strategyEngine, graphStrategySandbox,
                executionHandler, portfolioEngine, stageTimings, hotPathFeatureStore, deadLetterQueue, null);
    }

    public static PipelineComponents create(
            int shardCount,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue
    ) {
        return create(shardCount, positionRiskHandler, candleAggregationService, strategyEngine, null,
                executionHandler, portfolioEngine, stageTimings, hotPathFeatureStore, deadLetterQueue, null);
    }

    /**
     * Create and wire the hot-path pipeline components (no sandbox, with pipeline runtime bridge).
     */
    public static PipelineComponents create(
            int shardCount,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue,
            PipelineRuntimeBridge pipelineRuntimeBridge
    ) {
        return create(shardCount, positionRiskHandler, candleAggregationService, strategyEngine, null,
                executionHandler, portfolioEngine, stageTimings, hotPathFeatureStore, deadLetterQueue, pipelineRuntimeBridge);
    }

    /**
     * Deep create with all optional components including graph strategy sandbox.
     */
    public static PipelineComponents create(
            int shardCount,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue,
            PipelineRuntimeBridge pipelineRuntimeBridge
    ) {
        Objects.requireNonNull(positionRiskHandler, "positionRiskHandler must not be null");
        Objects.requireNonNull(candleAggregationService, "candleAggregationService must not be null");
        Objects.requireNonNull(strategyEngine, "strategyEngine must not be null");
        Objects.requireNonNull(executionHandler, "executionHandler must not be null");

        EventBus eventBus;
        if (shardCount > 1) {
            eventBus = new ShardedDisruptorEventBus(
                    shardCount,
                    positionRiskHandler,
                    candleAggregationService,
                    strategyEngine,
                    graphStrategySandbox,
                    executionHandler,
                    portfolioEngine,
                    stageTimings,
                    hotPathFeatureStore,
                    deadLetterQueue,
                    pipelineRuntimeBridge
            );
        } else {
            eventBus = new DisruptorEventBus(
                    positionRiskHandler,
                    candleAggregationService,
                    strategyEngine,
                    graphStrategySandbox,
                    executionHandler,
                    portfolioEngine,
                    stageTimings,
                    hotPathFeatureStore,
                    deadLetterQueue,
                    pipelineRuntimeBridge
            );
        }

        DisruptorBusMetrics busMetrics = (DisruptorBusMetrics) eventBus;

        Consumer<DomainEvent> busPublisher = eventBus::publish;
        MarketDataPipeline marketDataPipeline = new MarketDataPipeline(busPublisher);
        OrderPipeline orderPipeline = new OrderPipeline(busPublisher);

        log.info("PipelineConfig assembled components: eventBus={} shards={} ringSize={}",
                eventBus.getClass().getSimpleName(), busMetrics.shardCount(), busMetrics.ringBufferSize());

        return new PipelineComponents(eventBus, busMetrics, marketDataPipeline, orderPipeline);
    }

    /**
     * The assembled hot-path pipeline components.
     *
     * <p>Consumed by the Spring configuration layer to expose individual
     * components as beans or to start/stop the pipeline.
     *
     * @param eventBus           the Disruptor-backed event bus
     * @param marketDataPipeline the market data hot-path orchestrator
     * @param orderPipeline      the order lifecycle orchestrator
     */
    public record PipelineComponents(
            EventBus eventBus,
            DisruptorBusMetrics busMetrics,
            MarketDataPipeline marketDataPipeline,
            OrderPipeline orderPipeline
    ) {
    }
}
