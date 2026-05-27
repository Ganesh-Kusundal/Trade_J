package com.tradej.core.domain.event;

public record EventBusBackpressure(
        EventMetadata metadata,
        int queueSize,
        int maxSize,
        double utilizationPct
) implements DomainEvent {
    @Override
    public EventPriority priority() {
        return EventPriority.URGENT;
    }
}
