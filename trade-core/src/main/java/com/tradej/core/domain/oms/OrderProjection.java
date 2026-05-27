package com.tradej.core.domain.oms;

/**
 * Immutable read model projected from the event stream.
 * Represents the current state of an order after replaying all its events.
 */
public record OrderProjection(
        String orderId,
        String symbol,
        long totalQuantity,
        long filledQuantity,
        long averagePricePaisa,
        LifecycleState status
) {
    public boolean isFinal() {
        return status == LifecycleState.FILLED
                || status == LifecycleState.CANCELLED
                || status == LifecycleState.REJECTED
                || status == LifecycleState.EXPIRED;
    }

    public boolean hasOpenPosition() {
        return status == LifecycleState.SUBMITTED
                || status == LifecycleState.PARTIALLY_FILLED
                || status == LifecycleState.CANCEL_PENDING;
    }
}
