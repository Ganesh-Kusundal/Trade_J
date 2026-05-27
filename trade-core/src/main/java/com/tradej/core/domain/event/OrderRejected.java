package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Order;

public record OrderRejected(
        EventMetadata metadata,
        Order order,
        String reason
) implements DomainEvent {
    @Override
    public EventPriority priority() {
        return EventPriority.URGENT;
    }
}
