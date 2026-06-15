package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Order;

/**
 * Canonical order cancelled event — broker confirmed cancellation.
 */
public record OrderCancelled(
        EventMetadata metadata,
        Order order,
        String reason
) implements OrderUpdateEvent, OsmEventConvertible {
    public OrderCancelled {
        if (reason == null) {
            reason = "";
        }
    }

    @Override
    public com.tradej.core.domain.oms.OrderEvent toOsmEvent() {
        return new com.tradej.core.domain.oms.OrderCancelled(order.orderId());
    }

    @Override
    public com.tradej.core.domain.oms.OrderEvent toOsmEvent(String omsOrderId) {
        return new com.tradej.core.domain.oms.OrderCancelled(omsOrderId);
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
