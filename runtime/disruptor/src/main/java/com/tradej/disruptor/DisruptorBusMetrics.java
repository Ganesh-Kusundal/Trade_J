package com.tradej.disruptor;

/**
 * Observable metrics for a Disruptor-backed event bus (single shard or aggregated sharded view).
 */
public interface DisruptorBusMetrics {

    long ringBufferRemainingCapacity();

    int ringBufferSize();

    int dispatchQueueDepth();

    long dispatchDroppedEventCount();

    int subscriberCount();

    boolean isStarted();

    default int shardCount() {
        return 1;
    }
}
