package com.tradej.core.domain.oms;

/**
 * Sealed interface for all OSM events that drive the {@link OrderStateMachine}.
 * Each event type represents an action or notification in the order lifecycle.
 */
public sealed interface OrderEvent permits
        OrderSubmitted,
        OrderAcknowledged,
        OrderPartiallyFilled,
        OrderFullyFilled,
        OrderCancelled,
        OrderRejected,
        OrderExpired,
        CancelRequested {

    String orderId();

    EventType type();

    enum EventType {
        SUBMITTED,
        ACKNOWLEDGED,
        PARTIALLY_FILLED,
        FULLY_FILLED,
        CANCELLED,
        REJECTED,
        EXPIRED,
        CANCEL_REQUESTED
    }
}
