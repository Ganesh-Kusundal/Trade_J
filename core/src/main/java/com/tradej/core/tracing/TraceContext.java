package com.tradej.core.tracing;

import com.tradej.core.domain.event.EventMetadata;

/**
 * Thread-local trace context for correlating events across the pipeline.
 * Propagates correlation IDs from broker tick ingestion through strategy
 * evaluation, execution, and gateway delivery.
 */
public final class TraceContext {

    private static final ThreadLocal<String> CURRENT_CORRELATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> TRACE_START_NS = new ThreadLocal<>();

    private TraceContext() {}

    public static void setCorrelationId(String correlationId) {
        CURRENT_CORRELATION_ID.set(correlationId);
        TRACE_START_NS.set(System.nanoTime());
    }

    public static String getCorrelationId() {
        return CURRENT_CORRELATION_ID.get();
    }

    public static long elapsedNanos() {
        Long start = TRACE_START_NS.get();
        return start != null ? System.nanoTime() - start : 0;
    }

    public static long elapsedMicros() {
        return elapsedNanos() / 1_000;
    }

    public static void clear() {
        CURRENT_CORRELATION_ID.remove();
        TRACE_START_NS.remove();
    }

    /**
     * Extract correlation ID from event metadata and set it on the current thread.
     */
    public static void fromEvent(EventMetadata metadata) {
        if (metadata != null && metadata.correlationId() != null) {
            setCorrelationId(metadata.correlationId());
        }
    }

    /**
     * Run a task within a trace context, ensuring cleanup.
     */
    public static <T> T traced(String correlationId, java.util.function.Supplier<T> task) {
        setCorrelationId(correlationId);
        try {
            return task.get();
        } finally {
            clear();
        }
    }

    public static void traced(String correlationId, Runnable task) {
        setCorrelationId(correlationId);
        try {
            task.run();
        } finally {
            clear();
        }
    }
}
