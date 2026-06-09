package com.tradej.pipeline.runtime;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe performance metrics tracker for pipeline execution nodes.
 * Used for encapsulating execution metrics compositionally.
 */
public final class NodeMetricsTracker {

    private final AtomicLong processedCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong lastProcessedTimestampMs = new AtomicLong();
    private final AtomicLong lastExecutionNs = new AtomicLong();
    private final AtomicLong totalExecutionNs = new AtomicLong();

    /**
     * Record a successful event processing.
     *
     * @param durationNs the duration of execution in nanoseconds
     */
    public void recordSuccess(long durationNs) {
        processedCount.incrementAndGet();
        lastProcessedTimestampMs.set(System.currentTimeMillis());
        lastExecutionNs.set(durationNs);
        totalExecutionNs.addAndGet(durationNs);
    }

    /**
     * Record a failed event processing.
     *
     * @param durationNs the duration of execution in nanoseconds
     */
    public void recordFailure(long durationNs) {
        processedCount.incrementAndGet();
        errorCount.incrementAndGet();
        lastExecutionNs.set(durationNs);
        totalExecutionNs.addAndGet(durationNs);
    }

    /**
     * Returns a snapshot of the current node metrics.
     */
    public NodeMetrics getMetrics() {
        long count = processedCount.get();
        double avg = count == 0 ? 0.0 : (double) totalExecutionNs.get() / count;
        return new NodeMetrics(
                count,
                errorCount.get(),
                lastProcessedTimestampMs.get(),
                lastExecutionNs.get(),
                avg
        );
    }
}
