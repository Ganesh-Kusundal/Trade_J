package com.tradej.core.domain.oms;

public record OrderPartiallyFilled(String orderId, long filledQuantity, long pricePaisa) implements OrderEvent {
    public static OrderPartiallyFilled event(String orderId, long filledQuantity, long pricePaisa) {
        return new OrderPartiallyFilled(orderId, filledQuantity, pricePaisa);
    }

    @Override
    public EventType type() {
        return EventType.PARTIALLY_FILLED;
    }
}
