package com.tradej.core.domain.event;

public record StreamHealthChanged(
        EventMetadata metadata,
        String broker,
        String status,
        int reconnectAttempt
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
