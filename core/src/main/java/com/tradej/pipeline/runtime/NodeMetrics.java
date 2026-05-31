package com.tradej.pipeline.runtime;

public record NodeMetrics(
        long processedCount,
        long errorCount,
        long lastProcessedTimestampMs,
        long lastExecutionNs,
        double averageExecutionNs
) {
    public static final NodeMetrics ZERO = new NodeMetrics(0, 0, 0, 0, 0.0);
}
