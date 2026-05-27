package com.tradej.core.domain.oms;

public record OrderCancelled(String orderId) implements OrderEvent {
    public static OrderCancelled event(String orderId) {
        return new OrderCancelled(orderId);
    }

    @Override
    public EventType type() {
        return EventType.CANCELLED;
    }
}
