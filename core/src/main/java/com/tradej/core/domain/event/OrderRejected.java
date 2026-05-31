package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Order;

public record OrderRejected(
        EventMetadata metadata,
        Order order,
        String reason
) implements OrderUpdateEvent {
    @Override
    public EventPriority priority() {
        return EventPriority.URGENT;
    }

    /**
     * Convert to an OSM {@link com.tradej.core.domain.oms.OrderRejected} event for persistence.
     * The {@code omsOrderId} is the local OSM-generated order ID (not the broker exchange order ID).
     * This factory ensures the rejection reason is always consistent between the domain event
     * emitted downstream and the event stored in the OSM event log.
     */
    public com.tradej.core.domain.oms.OrderRejected toOsmEvent(String omsOrderId) {
        return new com.tradej.core.domain.oms.OrderRejected(omsOrderId, reason);
    }
}
