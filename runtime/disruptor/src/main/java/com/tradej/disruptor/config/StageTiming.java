package com.tradej.disruptor.config;

/**
 * Callback interface for recording per-stage event processing latency.
 *
 * <p>Each Disruptor stage handler calls {@link #record(long)} with the elapsed
 * nanoseconds after processing each event. A no-op implementation is provided
 * for production paths where timing is not configured.
 *
 * <p>Micrometer-backed implementations are created by the Spring configuration
 * layer in {@code trade-app} which has access to {@code MeterRegistry}.
 */
@FunctionalInterface
public interface StageTiming {

    /**
     * Record the elapsed time for processing a single event.
     *
     * @param elapsedNanos time spent processing the event in nanoseconds
     */
    void record(long elapsedNanos);

    /**
     * Returns a no-op timing instance that discards all measurements.
     */
    static StageTiming noOp() {
        return nanos -> {};
    }
}
