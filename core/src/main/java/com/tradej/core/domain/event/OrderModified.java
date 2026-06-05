package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Order;

/**
 * Canonical order modified event — broker confirmed modification.
 */
public record OrderModified(
        EventMetadata metadata,
        Order order
) implements OrderUpdateEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
