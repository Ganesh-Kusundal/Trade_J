package com.tradej.brokergateway.simulation;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Virtual clock for backtesting and replay scenarios.
 * Provides deterministic time control instead of wall-clock time.
 * Time advances monotonically — it never moves backward.
 */
public final class BrokerClock {

    private final AtomicLong timeMs;

    /**
     * Creates a clock starting at the current system time.
     */
    public BrokerClock() {
        this(System.currentTimeMillis());
    }

    /**
     * Creates a clock starting at the specified timestamp.
     *
     * @param startTimeMs the initial time in epoch milliseconds
     */
    public BrokerClock(long startTimeMs) {
        this.timeMs = new AtomicLong(startTimeMs);
    }

    /**
     * Returns the current virtual time in epoch milliseconds.
     */
    public long currentTimeMs() {
        return timeMs.get();
    }

    /**
     * Advances the clock to the given timestamp.
     * If the timestamp is before the current time, this is a no-op (monotonic).
     *
     * @param timestampMs the target time in epoch milliseconds
     */
    public void advanceTo(long timestampMs) {
        timeMs.updateAndGet(current -> Math.max(current, timestampMs));
    }

    /**
     * Sets the clock to an exact timestamp, even if it moves backward.
     * Use with care — prefer {@link #advanceTo(long)} for replay scenarios.
     *
     * @param timestampMs the target time in epoch milliseconds
     */
    public void set(long timestampMs) {
        timeMs.set(timestampMs);
    }

    /**
     * Advances the clock by the given duration.
     *
     * @param deltaMs duration to advance in milliseconds
     */
    public void advanceBy(long deltaMs) {
        timeMs.addAndGet(deltaMs);
    }
}
