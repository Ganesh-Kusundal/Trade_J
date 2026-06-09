package com.tradej.execution.service;

import com.tradej.core.domain.event.DomainEvent;

import java.util.function.Consumer;

/**
 * Configuration for {@link ExecutionHandler}.
 * Replaces the former 7-constructor sprawl with a single config record.
 */
public record ExecutionConfig(
        int queueCapacity,
        long orderPlacementTimeoutMs,
        int partitionCount,
        Consumer<DomainEvent> downstream
) {
    public static final int DEFAULT_QUEUE_CAPACITY = 1000;
    public static final long DEFAULT_ORDER_PLACEMENT_TIMEOUT_MS = 10_000L;
    public static final int DEFAULT_PARTITION_COUNT = 4;

    public static final ExecutionConfig DEFAULTS = new ExecutionConfig(
            DEFAULT_QUEUE_CAPACITY, DEFAULT_ORDER_PLACEMENT_TIMEOUT_MS, DEFAULT_PARTITION_COUNT, null);

    public ExecutionConfig withQueueCapacity(int capacity) {
        return new ExecutionConfig(capacity, orderPlacementTimeoutMs, partitionCount, downstream);
    }

    public ExecutionConfig withTimeout(long timeoutMs) {
        return new ExecutionConfig(queueCapacity, timeoutMs, partitionCount, downstream);
    }

    public ExecutionConfig withPartitions(int count) {
        return new ExecutionConfig(queueCapacity, orderPlacementTimeoutMs, count, downstream);
    }

    public ExecutionConfig withDownstream(Consumer<DomainEvent> dl) {
        return new ExecutionConfig(queueCapacity, orderPlacementTimeoutMs, partitionCount, dl);
    }
}
