package com.tradej.core.domain.event;

/**
 * Canonical sealed interface for order lifecycle events from any broker adapter.
 * Every broker adapter MUST emit one of these subtypes — never broker-specific order types.
 * The OMS and strategy layer consume ONLY these canonical events.
 */
public sealed interface OrderUpdateEvent extends DomainEvent
        permits OrderAccepted,
                OrderRejected,
                OrderFilled,
                OrderPartiallyFilled,
                OrderFullyFilled,
                OrderModified,
                OrderCancelled {
}
