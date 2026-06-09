package com.tradej.core.domain.event;

/**
 * Visitor for order lifecycle events: acceptance, fills, modifications, and cancellations.
 */
public interface OrderLifecycleVisitor {
    default void visit(OrderAccepted event) {}
    default void visit(OrderFilled event) {}
    default void visit(OrderRejected event) {}
    default void visit(OrderCancelled event) {}
    default void visit(OrderModified event) {}
    default void visit(OrderPartiallyFilled event) {}
    default void visit(OrderFullyFilled event) {}
    default void visit(OrderUpdateEvent event) {}
}
