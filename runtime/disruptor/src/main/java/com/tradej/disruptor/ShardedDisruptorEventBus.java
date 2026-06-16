package com.tradej.disruptor;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.disruptor.config.DisruptorPipelineConfig;
import com.tradej.core.routing.SymbolShardRouter;
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

    /**
     * Creates a sharded event bus from a pipeline configuration.
     * Each shard gets its own {@link DisruptorEventBus} with the same config.
     */
    public ShardedDisruptorEventBus(int shardCount, DisruptorPipelineConfig config) {
        if (shardCount < 1) {
            throw new IllegalArgumentException("shardCount must be >= 1");
        }
        this.shardCount = shardCount;
        this.shards = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            shards.add(new DisruptorEventBus(config));
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
    public int downstreamQueueDepth() {
        return shards.stream().mapToInt(DisruptorEventBus::downstreamQueueDepth).sum();
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
