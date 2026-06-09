package com.tradej.persistence.replay;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics for replay engine operations.
 * Tracks events replayed, failed, duration, and throughput.
 */
public final class ReplayMetrics {

    private final AtomicLong eventsReplayed = new AtomicLong();
    private final AtomicLong eventsFailed = new AtomicLong();
    private final AtomicLong totalDurationMs = new AtomicLong();
    private final AtomicLong replayCount = new AtomicLong();
    private final AtomicLong maxDurationMs = new AtomicLong();

    public void recordReplay(long replayed, long failed, long durationMs) {
        eventsReplayed.addAndGet(replayed);
        eventsFailed.addAndGet(failed);
        totalDurationMs.addAndGet(durationMs);
        replayCount.incrementAndGet();
        updateMax(durationMs);
    }

    private void updateMax(long durationMs) {
        long current;
        do {
            current = maxDurationMs.get();
            if (durationMs <= current) return;
        } while (!maxDurationMs.compareAndSet(current, durationMs));
    }

    public long eventsReplayed() { return eventsReplayed.get(); }
    public long eventsFailed() { return eventsFailed.get(); }
    public long replayCount() { return replayCount.get(); }
    public long totalDurationMs() { return totalDurationMs.get(); }
    public long maxDurationMs() { return maxDurationMs.get(); }

    public long averageDurationMs() {
        long count = replayCount.get();
        return count == 0 ? 0 : totalDurationMs.get() / count;
    }

    public double successRate() {
        long total = eventsReplayed.get() + eventsFailed.get();
        return total == 0 ? 1.0 : (double) eventsReplayed.get() / total;
    }

    public long eventsPerSecond() {
        long duration = totalDurationMs.get();
        return duration == 0 ? 0 : eventsReplayed.get() * 1000 / duration;
    }

    public void reset() {
        eventsReplayed.set(0);
        eventsFailed.set(0);
        totalDurationMs.set(0);
        replayCount.set(0);
        maxDurationMs.set(0);
    }

    @Override
    public String toString() {
        return String.format(
            "ReplayMetrics{replays=%d, events=%d replayed/%d failed, successRate=%.1f%%, " +
            "avgDuration=%dms, maxDuration=%dms, throughput=%d events/sec}",
            replayCount(), eventsReplayed(), eventsFailed(),
            successRate() * 100, averageDurationMs(), maxDurationMs(), eventsPerSecond());
    }
}
