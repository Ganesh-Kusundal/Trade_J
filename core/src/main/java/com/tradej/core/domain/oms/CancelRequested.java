package com.tradej.core.domain.oms;

public record CancelRequested(String orderId) implements OrderEvent {
    public static CancelRequested event(String orderId) {
        return new CancelRequested(orderId);
    }

    @Override
    public EventType type() {
        return EventType.CANCEL_REQUESTED;
    }
}
