package com.tradej.persistence.replay;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.port.EventBus;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives the replay clock. Advances the simulated time during REPLAY mode
 * and publishes {@link ReplayTimeChangedEvent} so downstream consumers
 * (e.g. gateway, read model) can stay synchronized with replay progress.
 */
public final class ReplayClock implements AutoCloseable {

    private static final long DEFAULT_REPLAY_SPEED_NANOS = 1_000_000L; // 1ms wall-clock per event

    private final EventBus eventBus;
    private final AtomicLong currentTimeMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong replaySpeedNanos = new AtomicLong(DEFAULT_REPLAY_SPEED_NANOS);

    public ReplayClock(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Advance the replay clock to the given timestamp and publish the change event.
     */
    public void advanceTo(long timestampMs) {
        long previous;
        long current;
        synchronized (this) {
            previous = currentTimeMs.get();
            if (timestampMs <= previous) {
                return;
            }
            currentTimeMs.set(timestampMs);
            current = timestampMs;
        }
        eventBus.publish(new ReplayTimeChangedEvent(
                EventMetadata.root(),
                current,
                replaySpeedNanos.get()
        ));
    }

    /**
     * Returns the current replay time in milliseconds.
     */
    public long currentTimeMs() {
        return currentTimeMs.get();
    }

    /**
     * Sets the replay speed multiplier (nanoseconds per tick).
     */
    public void setReplaySpeedNanos(long speedNanos) {
        this.replaySpeedNanos.set(speedNanos);
    }

    /**
     * Returns the configured replay speed in nanoseconds.
     */
    public long replaySpeedNanos() {
        return replaySpeedNanos.get();
    }

    @Override
    public void close() {
        // no-op; maintained for AutoCloseable interface
    }
}
