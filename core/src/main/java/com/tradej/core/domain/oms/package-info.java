/**
 * Order Management System — deterministic state machine and event-sourced order lifecycle.
 *
 * <h3>Why Two OrderEvent Hierarchies</h3>
 * Order lifecycle events are intentionally split between this package and
 * {@code core.domain.event}:
 * <ul>
 *   <li>{@link OrderEvent} subtypes here are <b>compact records</b> optimized for
 *       Chronicle Queue persistence and fast replay. They carry only the minimum
 *       fields needed for OMS state transitions (orderId, reason, quantity, etc.).</li>
 *   <li>{@code core.domain.event.OrderUpdateEvent} subtypes are <b>rich domain events</b>
 *       with {@link com.tradej.core.domain.event.EventMetadata}, full {@code Order}
 *       snapshots, and {@code Trade} fills. They flow through the EventBus to
 *       downstream subscribers (gateway, read model, analytics).</li>
 * </ul>
 *
 * <p>This follows <b>CQRS and Event Sourcing</b> best practices: the write-side
 * uses compact events for storage efficiency, while the read-side uses enriched
 * events for consumer convenience. The conversion between them is standardized
 * via {@link com.tradej.core.domain.event.OsmEventConvertible}.
 *
 * <h3>Primary Consumers</h3>
 * <ul>
 *   <li>{@link OrderStateMachine} — drives order lifecycle transitions</li>
 *   <li>{@code EventSourcedOrderRepository} — persists events to Chronicle Queue</li>
 *   <li>{@code OrderManagementService} — coordinates broker events with OMS state</li>
 *   <li>{@code ExecutionHandler} — processes order lifecycle events in the signal pipeline</li>
 * </ul>
 *
 * @see com.tradej.core.domain.event.OsmEventConvertible
 * @see com.tradej.core.domain.event.OrderUpdateEvent
 */
package com.tradej.core.domain.oms;
