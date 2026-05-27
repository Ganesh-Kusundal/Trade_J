package com.tradej.core.domain.oms;

public record OrderFullyFilled(String orderId, long totalQuantity, long pricePaisa) implements OrderEvent {
    public static OrderFullyFilled event(String orderId, long totalQuantity, long pricePaisa) {
        return new OrderFullyFilled(orderId, totalQuantity, pricePaisa);
    }

    @Override
    public EventType type() {
        return EventType.FULLY_FILLED;
    }
}
