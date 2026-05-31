package com.tradej.core.domain.event;

public record PositionMismatch(
        EventMetadata metadata,
        String symbol,
        long paperQuantity,
        long brokerQuantity,
        String engineKey
) implements DomainEvent {
    @Override
    public EventPriority priority() {
        return EventPriority.URGENT;
    }
}
