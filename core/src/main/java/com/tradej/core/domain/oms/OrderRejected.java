package com.tradej.core.domain.oms;

public record OrderRejected(String orderId, String reason) implements OrderEvent {
    public static OrderRejected event(String orderId, String reason) {
        return new OrderRejected(orderId, reason);
    }

    @Override
    public EventType type() {
        return EventType.REJECTED;
    }
}
