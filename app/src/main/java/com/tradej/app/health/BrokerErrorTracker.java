package com.tradej.app.health;

import com.tradej.core.domain.event.BrokerAdapterError;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks broker API failures by counting {@link BrokerAdapterError} events
 * and recording the most recent error details. Designed to be consumed by
 * {@link BrokerHealthIndicator} for exposing error information via the
 * Spring Boot Actuator health endpoint.
 */
@Service
public final class BrokerErrorTracker implements DomainEventHandler<DomainEvent> {

    private final AtomicLong totalErrors = new AtomicLong();
    private final Map<String, AtomicLong> errorsBySource = new ConcurrentHashMap<>();
    private volatile long lastErrorTimestampMs;
    private volatile String lastErrorSource;
    private volatile String lastErrorDetail;

    /**
     * Accepts any {@link DomainEvent} and counts {@link BrokerAdapterError} events.
     */
    @Override
    public void onEvent(DomainEvent event) {
        if (event instanceof BrokerAdapterError error) {
            totalErrors.incrementAndGet();
            errorsBySource.computeIfAbsent(error.stage(), ignored -> new AtomicLong()).incrementAndGet();
            lastErrorTimestampMs = System.currentTimeMillis();
            lastErrorSource = error.stage();
            lastErrorDetail = error.detail();
        }
    }

    // ── Query methods for health indicator ──

    /** Total number of broker errors recorded since startup. */
    public long totalErrors() {
        return totalErrors.get();
    }

    /** Unmodifiable map of error source → error count. */
    public Map<String, Long> errorsBySource() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        errorsBySource.forEach((source, count) -> snapshot.put(source, count.get()));
        return Collections.unmodifiableMap(snapshot);
    }

    /** Timestamp (epoch ms) of the most recent broker error, or 0 if none. */
    public long lastErrorTimestampMs() {
        return lastErrorTimestampMs;
    }

    /** Source/stage of the most recent broker error, or empty string if none. */
    public String lastErrorSource() {
        return lastErrorSource == null ? "" : lastErrorSource;
    }

    /** Detail message of the most recent broker error, or empty string if none. */
    public String lastErrorDetail() {
        return lastErrorDetail == null ? "" : lastErrorDetail;
    }

    /** Resets all counters and last-error state. */
    public void reset() {
        totalErrors.set(0L);
        errorsBySource.clear();
        lastErrorTimestampMs = 0L;
        lastErrorSource = null;
        lastErrorDetail = null;
    }
}
