package com.tradej.core.domain.event;

import com.tradej.core.domain.oms.OrderEvent;

/**
 * Marks a domain event that can be converted to a lightweight OMS event
 * for persistence in {@link com.tradej.persistence.oms.EventSourcedOrderRepository}.
 *
 * <h3>Why Two Hierarchies Exist</h3>
 * <p>Order lifecycle events are intentionally split across two packages:
 * <ul>
 *   <li>{@code core.domain.oms.OrderEvent} — Lightweight records for the OMS state machine
 *       and Chronicle Queue event sourcing. These carry only the minimum fields needed
 *       for state transitions, keeping persistence compact and replay fast.</li>
 *   <li>{@code core.domain.event.OrderUpdateEvent} — Rich domain events with full
 *       {@link EventMetadata}, {@link com.tradej.core.domain.model.Order} snapshots,
 *       and {@link com.tradej.core.domain.model.Trade} fills. These flow through the
 *       EventBus to downstream subscribers (gateway, read model, analytics).</li>
 * </ul>
 *
 * <p>This follows <b>CQRS and Event Sourcing</b> best practices: the write-side
 * (OMS) uses compact events optimized for storage and replay, while the read-side
 * (domain events) uses enriched events optimized for subscribers.
 *
 * <p>Every {@link OrderUpdateEvent} subtype that has a corresponding {@link OrderEvent}
 * subtype SHOULD implement this interface to standardize the conversion path.
 *
 * @see OrderUpdateEvent
 * @see com.tradej.core.domain.oms.OrderEvent
 */
public interface OsmEventConvertible {

    /**
     * Converts this rich domain event to a lightweight OMS event for persistence,
     * using the event's own order ID (typically the broker exchange order ID).
     *
     * <p>Prefer {@link #toOsmEvent(String)} when the caller has access to the
     * internal OMS-generated order ID, as it avoids a lookup in the identity registry.
     *
     * @return a compact OMS event suitable for event sourcing
     */
    OrderEvent toOsmEvent();

    /**
     * Converts this rich domain event to a lightweight OMS event for persistence,
     * using the provided internal OMS order ID.
     *
     * <p>This overload should be used when the caller (e.g., ExecutionHandler) has
     * already resolved the internal OMS order ID from the identity registry. It
     * avoids the double-lookup from broker exchange order ID to internal order ID.
     *
     * @param omsOrderId the internal OMS-generated order ID (e.g., "ORD-{epochMs}-{seq}")
     * @return a compact OMS event suitable for event sourcing
     */
    default OrderEvent toOsmEvent(String omsOrderId) {
        return toOsmEvent();
    }
}
