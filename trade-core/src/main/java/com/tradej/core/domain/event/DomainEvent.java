package com.tradej.core.domain.event;

public interface DomainEvent {
    EventMetadata metadata();

    default String eventId() {
        return metadata().eventId();
    }

    default long timestampMs() {
        return metadata().timestampMs();
    }

    default long timestampMonotonic() {
        return metadata().timestampMonotonic();
    }

    default long sequenceId() {
        return metadata().sequenceId();
    }

    default String correlationId() {
        return metadata().correlationId();
    }

    default EventPriority priority() {
        return EventPriority.NORMAL;
    }
}
