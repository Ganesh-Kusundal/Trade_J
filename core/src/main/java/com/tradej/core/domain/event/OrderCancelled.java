package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Order;

/**
 * Canonical order cancelled event — broker confirmed cancellation.
 */
public record OrderCancelled(
        EventMetadata metadata,
        Order order,
        String reason
) implements OrderUpdateEvent {
    public OrderCancelled {
        if (reason == null) {
            reason = "";
        }
    }
}
