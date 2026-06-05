package com.tradej.core.domain.event;

public record BrokerAdapterError(
        EventMetadata metadata,
        String broker,
        String stage,
        String detail
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
