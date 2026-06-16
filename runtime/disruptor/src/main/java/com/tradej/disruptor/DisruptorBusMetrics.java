package com.tradej.disruptor;

/**
 * Observable metrics for a Disruptor-backed event bus (single shard or aggregated sharded view).
 */
public interface DisruptorBusMetrics {

    long ringBufferRemainingCapacity();

    int ringBufferSize();

    int dispatchQueueDepth();

    long dispatchDroppedEventCount();

    /**
     * Number of events waiting in the downstream (re-entrant) queue.
     * High values indicate subscriber-triggered event storms.
     */
    int downstreamQueueDepth();

    int subscriberCount();

    boolean isStarted();

    default int shardCount() {
        return 1;
    }
}
