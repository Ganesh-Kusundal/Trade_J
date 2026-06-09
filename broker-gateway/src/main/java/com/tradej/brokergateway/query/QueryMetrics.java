package com.tradej.brokergateway.query;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics for the DuckDB query engine.
 * Tracks query count, latency, errors, and cache hit ratio.
 */
public final class QueryMetrics {

    private final AtomicLong queriesExecuted = new AtomicLong();
    private final AtomicLong queriesFailed = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private final AtomicLong maxLatencyMs = new AtomicLong();
    private final AtomicLong rowsReturned = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();

    public void recordQuery(long latencyMs, long rows) {
        queriesExecuted.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        rowsReturned.addAndGet(rows);
        updateMax(latencyMs);
    }

    public void recordFailure() {
        queriesExecuted.incrementAndGet();
        queriesFailed.incrementAndGet();
    }

    public void recordCacheHit() { cacheHits.incrementAndGet(); }
    public void recordCacheMiss() { cacheMisses.incrementAndGet(); }

    private void updateMax(long latencyMs) {
        long current;
        do {
            current = maxLatencyMs.get();
            if (latencyMs <= current) return;
        } while (!maxLatencyMs.compareAndSet(current, latencyMs));
    }

    public long queriesExecuted() { return queriesExecuted.get(); }
    public long queriesFailed() { return queriesFailed.get(); }
    public long totalLatencyMs() { return totalLatencyMs.get(); }
    public long maxLatencyMs() { return maxLatencyMs.get(); }
    public long rowsReturned() { return rowsReturned.get(); }
    public long cacheHits() { return cacheHits.get(); }
    public long cacheMisses() { return cacheMisses.get(); }

    public long averageLatencyMs() {
        long executed = queriesExecuted.get();
        return executed == 0 ? 0 : totalLatencyMs.get() / executed;
    }

    public double cacheHitRatio() {
        long total = cacheHits.get() + cacheMisses.get();
        return total == 0 ? 0.0 : (double) cacheHits.get() / total;
    }

    public void reset() {
        queriesExecuted.set(0);
        queriesFailed.set(0);
        totalLatencyMs.set(0);
        maxLatencyMs.set(0);
        rowsReturned.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
    }

    @Override
    public String toString() {
        return String.format(
            "QueryMetrics{queries=%d, failed=%d, avgLatency=%dms, maxLatency=%dms, " +
            "rows=%d, cacheHitRatio=%.1f%%}",
            queriesExecuted(), queriesFailed(), averageLatencyMs(), maxLatencyMs(),
            rowsReturned(), cacheHitRatio() * 100);
    }
}
