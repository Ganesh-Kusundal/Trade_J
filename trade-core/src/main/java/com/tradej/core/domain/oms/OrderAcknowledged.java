package com.tradej.core.domain.oms;

public record OrderAcknowledged(String orderId, String exchangeOrderId) implements OrderEvent {
    public static OrderAcknowledged event(String orderId, String exchangeOrderId) {
        return new OrderAcknowledged(orderId, exchangeOrderId);
    }

    @Override
    public EventType type() {
        return EventType.ACKNOWLEDGED;
    }
}
