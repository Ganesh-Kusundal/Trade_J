package com.tradej.core.domain.port;

import com.tradej.core.domain.event.DomainEvent;

/**
 * Append-only dead-letter queue for events that could not be processed on the hot path.
 */
public interface DeadLetterQueue {

    /**
     * Records a dropped or failed event for later inspection and replay.
     *
     * @param source   component that rejected the event (e.g. {@code execution-handler})
     * @param event    the domain event that was dropped
     * @param reason   human-readable rejection reason
     */
    void append(String source, DomainEvent event, String reason);

    /** No-op implementation for tests. */
    static DeadLetterQueue noop() {
        return (source, event, reason) -> { };
    }
}
