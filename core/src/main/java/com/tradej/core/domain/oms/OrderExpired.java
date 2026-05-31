package com.tradej.core.domain.oms;

public record OrderExpired(String orderId) implements OrderEvent {
    public static OrderExpired event(String orderId) {
        return new OrderExpired(orderId);
    }

    @Override
    public EventType type() {
        return EventType.EXPIRED;
    }
}
