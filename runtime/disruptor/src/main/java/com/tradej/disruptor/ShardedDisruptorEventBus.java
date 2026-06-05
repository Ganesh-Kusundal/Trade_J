package com.tradej.disruptor;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.pipeline.runtime.PipelineRuntimeBridge;
import com.tradej.core.routing.SymbolShardRouter;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import com.tradej.strategy.service.StrategyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Symbol-partitioned {@link EventBus} backed by one {@link DisruptorEventBus} per shard.
 *
 * <p>Events with a resolvable symbol are routed deterministically by symbol hash.
 * Events without a symbol are routed to shard 0.
 */
public final class ShardedDisruptorEventBus implements EventBus, DisruptorBusMetrics {

    private static final Logger log = LoggerFactory.getLogger(ShardedDisruptorEventBus.class);

    private final List<DisruptorEventBus> shards;
    private final int shardCount;

    public ShardedDisruptorEventBus(
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
        this(shardCount, positionRiskHandler, candleAggregationService, strategyEngine, null, executionHandler,
                portfolioEngine, stageTimings, hotPathFeatureStore, deadLetterQueue, pipelineRuntimeBridge);
    }

    public ShardedDisruptorEventBus(
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
        if (shardCount < 1) {
            throw new IllegalArgumentException("shardCount must be >= 1");
        }
        this.shardCount = shardCount;
        this.shards = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            shards.add(new DisruptorEventBus(
                    positionRiskHandler,
                    candleAggregationService,
                    strategyEngine,
                    graphStrategySandbox,
                    executionHandler,
                    portfolioEngine,
                    stageTimings,
                    hotPathFeatureStore,
                    deadLetterQueue,
                    pipelineRuntimeBridge,
                    i == 0
            ));
        }
        log.info("ShardedDisruptorEventBus initialized shardCount={} ringBufferSizePerShard={}",
                shardCount, shards.getFirst().ringBufferSize());
    }

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        for (DisruptorEventBus shard : shards) {
            shard.subscribe(eventType, handler);
        }
    }

    @Override
    public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        for (DisruptorEventBus shard : shards) {
            shard.unsubscribe(eventType, handler);
        }
    }

    @Override
    public void publish(DomainEvent event) {
        shards.get(shardIndex(event)).publish(event);
    }

    @Override
    public void start() {
        for (DisruptorEventBus shard : shards) {
            shard.start();
        }
    }

    @Override
    public void stop() {
        for (DisruptorEventBus shard : shards) {
            shard.stop();
        }
    }

    public DisruptorEventBus shard(int index) {
        return shards.get(index);
    }

    int shardIndex(DomainEvent event) {
        return shardIndexFor(event, shardCount);
    }

    static int shardIndexFor(DomainEvent event, int shardCount) {
        return SymbolShardRouter.shardFor(event, shardCount);
    }

    @Override
    public long ringBufferRemainingCapacity() {
        return shards.stream().mapToLong(DisruptorEventBus::ringBufferRemainingCapacity).min().orElse(0L);
    }

    @Override
    public int ringBufferSize() {
        return shards.stream().mapToInt(DisruptorEventBus::ringBufferSize).sum();
    }

    @Override
    public int dispatchQueueDepth() {
        return shards.stream().mapToInt(DisruptorEventBus::dispatchQueueDepth).sum();
    }

    @Override
    public long dispatchDroppedEventCount() {
        return shards.stream().mapToLong(DisruptorEventBus::dispatchDroppedEventCount).sum();
    }

    @Override
    public int subscriberCount() {
        return shards.isEmpty() ? 0 : shards.getFirst().subscriberCount();
    }

    @Override
    public boolean isStarted() {
        return shards.stream().allMatch(DisruptorEventBus::isStarted);
    }

    @Override
    public int shardCount() {
        return shardCount;
    }
}
