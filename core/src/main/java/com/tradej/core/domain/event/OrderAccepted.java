package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Order;

public record OrderAccepted(EventMetadata metadata, Order order) implements OrderUpdateEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
