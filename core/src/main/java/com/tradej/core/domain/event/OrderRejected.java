package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Order;

public record OrderRejected(
        EventMetadata metadata,
        Order order,
        String reason
) implements OrderUpdateEvent, OsmEventConvertible {
    @Override
    public EventPriority priority() {
        return EventPriority.URGENT;
    }

    @Override
    public com.tradej.core.domain.oms.OrderEvent toOsmEvent() {
        return new com.tradej.core.domain.oms.OrderRejected(
                order != null ? order.orderId() : "", reason);
    }

    @Override
    public com.tradej.core.domain.oms.OrderEvent toOsmEvent(String omsOrderId) {
        return new com.tradej.core.domain.oms.OrderRejected(omsOrderId, reason);
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
