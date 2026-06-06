package com.tradej.broker.core.chaos;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collected during a chaos scenario execution.
 */
public final class ChaosMetrics {
    private final AtomicInteger requestsAttempted = new AtomicInteger();
    private final AtomicInteger requestsSucceeded = new AtomicInteger();
    private final AtomicInteger requestsFailed = new AtomicInteger();
    private final AtomicInteger circuitBreakerTrips = new AtomicInteger();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private final AtomicLong maxLatencyMs = new AtomicLong();

    public void recordSuccess(long latencyMs) {
        requestsAttempted.incrementAndGet();
        requestsSucceeded.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        updateMax(latencyMs);
    }

    public void recordFailure() {
        requestsAttempted.incrementAndGet();
        requestsFailed.incrementAndGet();
    }

    public void recordCircuitBreakerTrip() { circuitBreakerTrips.incrementAndGet(); }
    public void recordReconnectAttempt() { reconnectAttempts.incrementAndGet(); }

    private void updateMax(long latencyMs) {
        long current;
        do {
            current = maxLatencyMs.get();
            if (latencyMs <= current) return;
        } while (!maxLatencyMs.compareAndSet(current, latencyMs));
    }

    public int requestsAttempted() { return requestsAttempted.get(); }
    public int requestsSucceeded() { return requestsSucceeded.get(); }
    public int requestsFailed() { return requestsFailed.get(); }
    public int circuitBreakerTrips() { return circuitBreakerTrips.get(); }
    public int reconnectAttempts() { return reconnectAttempts.get(); }
    public long averageLatencyMs() {
        int successes = requestsSucceeded.get();
        return successes == 0 ? 0 : totalLatencyMs.get() / successes;
    }
    public long maxLatencyMs() { return maxLatencyMs.get(); }
    public double successRate() {
        int attempted = requestsAttempted.get();
        return attempted == 0 ? 1.0 : (double) requestsSucceeded.get() / attempted;
    }

    @Override
    public String toString() {
        return String.format(
            "ChaosMetrics{attempted=%d, succeeded=%d, failed=%d, successRate=%.1f%%, " +
            "avgLatency=%dms, maxLatency=%dms, cbTrips=%d, reconnects=%d}",
            requestsAttempted(), requestsSucceeded(), requestsFailed(),
            successRate() * 100, averageLatencyMs(), maxLatencyMs(),
            circuitBreakerTrips(), reconnectAttempts());
    }
}
