package com.tradej.core.domain.oms;

public record OrderSubmitted(String orderId, String correlationId, String symbol, long totalQuantity) implements OrderEvent {
    public static OrderSubmitted event(String orderId) {
        return new OrderSubmitted(orderId, "", "", 0L);
    }

    public static OrderSubmitted create(String orderId, String correlationId, String symbol, long totalQuantity) {
        return new OrderSubmitted(orderId, correlationId, symbol, totalQuantity);
    }

    @Override
    public EventType type() {
        return EventType.SUBMITTED;
    }
}
