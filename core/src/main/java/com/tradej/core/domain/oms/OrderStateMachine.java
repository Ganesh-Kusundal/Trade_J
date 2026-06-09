package com.tradej.core.domain.oms;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic OMS state machine.
 * <p>
 * Transitions are defined by a lookup table keyed on {@code (currentState, eventType)}.
 * Invalid transitions throw {@link IllegalStateException}.
 * <p>
 * Thread-safe after construction; individual {@link #on(OrderEvent)} calls should be
 * synchronized externally when used concurrently for the same order.
 */
public final class OrderStateMachine {

    private static final Map<StateTransition, LifecycleState> TRANSITIONS = buildTransitions();

    private final String orderId;
    private final String symbol;
    private final long totalQuantity;
    private LifecycleState state;
    private long filledQuantity;
    private long accumulatedValuePaisa; // sum of (price × quantity) for VWAP

    public OrderStateMachine(String orderId, String symbol, long totalQuantity) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.totalQuantity = totalQuantity;
        this.state = LifecycleState.NEW;
        this.filledQuantity = 0L;
        this.accumulatedValuePaisa = 0L;
    }

    // --- Internal constructor for rebuilding from events ---
    private OrderStateMachine(String orderId, String symbol, long totalQuantity,
                              LifecycleState state, long filledQuantity, long accumulatedValuePaisa) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.totalQuantity = totalQuantity;
        this.state = state;
        this.filledQuantity = filledQuantity;
        this.accumulatedValuePaisa = accumulatedValuePaisa;
    }

    public synchronized void on(OrderEvent event) {
        if (!event.orderId().equals(orderId)) {
            throw new IllegalArgumentException(
                    "Event orderId [" + event.orderId() + "] does not match machine orderId [" + orderId + "]");
        }
        LifecycleState next = TRANSITIONS.get(new StateTransition(state, event.type()));
        if (next == null) {
            throw new IllegalStateException(
                    "Invalid transition: " + state + " -> " + event.type());
        }

        // Accumulate fill data
        switch (event) {
            case OrderPartiallyFilled fill -> {
                this.filledQuantity += fill.filledQuantity();
                this.accumulatedValuePaisa += fill.filledQuantity() * fill.pricePaisa();
            }
            case OrderFullyFilled fill -> {
                long targetFilled = Math.min(
                        Math.max(this.filledQuantity, fill.totalQuantity()),
                        this.totalQuantity);
                long additional = targetFilled - this.filledQuantity;
                if (additional > 0) {
                    this.accumulatedValuePaisa += additional * fill.pricePaisa();
                    this.filledQuantity = targetFilled;
                }
            }
            case CancelRequested ignored -> {}
            case OrderSubmitted ignored -> {}
            case OrderAcknowledged ignored -> {}
            case OrderCancelled ignored -> {}
            case OrderRejected ignored -> {}
            case OrderExpired ignored -> {}
        }

        this.state = next;
    }

    public synchronized OrderProjection toProjection() {
        return new OrderProjection(orderId, symbol, totalQuantity, filledQuantity, averagePricePaisa(), state);
    }

    /**
     * Returns the volume-weighted average price (VWAP) of all fills, or 0 if no fills yet.
     */
    public synchronized long averagePricePaisa() {
        return filledQuantity > 0 ? accumulatedValuePaisa / filledQuantity : 0L;
    }

    /**
     * Rebuild machine state by replaying events in order.
     */
    public static OrderStateMachine replay(String orderId, String symbol, long totalQuantity,
                                           Iterable<? extends OrderEvent> events) {
        OrderStateMachine machine = new OrderStateMachine(orderId, symbol, totalQuantity);
        for (OrderEvent event : events) {
            machine.on(event);
        }
        return machine;
    }

    /**
     * Rebuild machine state from projection fields (for deserialization).
     */
    static OrderStateMachine fromProjection(String orderId, String symbol, long totalQuantity,
                                            LifecycleState state, long filledQuantity, long accumulatedValuePaisa) {
        return new OrderStateMachine(orderId, symbol, totalQuantity, state, filledQuantity, accumulatedValuePaisa);
    }

    // --- Transition table ---

    private record StateTransition(LifecycleState from, OrderEvent.EventType event) {
    }

    private static Map<StateTransition, LifecycleState> buildTransitions() {
        Map<StateTransition, LifecycleState> map = new HashMap<>();

        // NEW → OrderSubmitted → PENDING_SUBMIT
        put(map, LifecycleState.NEW, OrderEvent.EventType.SUBMITTED, LifecycleState.PENDING_SUBMIT);
        // NEW → CancelRequested → CANCEL_PENDING
        put(map, LifecycleState.NEW, OrderEvent.EventType.CANCEL_REQUESTED, LifecycleState.CANCEL_PENDING);

        // PENDING_SUBMIT → OrderAcknowledged → SUBMITTED
        put(map, LifecycleState.PENDING_SUBMIT, OrderEvent.EventType.ACKNOWLEDGED, LifecycleState.SUBMITTED);
        // PENDING_SUBMIT → OrderRejected → REJECTED
        put(map, LifecycleState.PENDING_SUBMIT, OrderEvent.EventType.REJECTED, LifecycleState.REJECTED);
        // PENDING_SUBMIT → OrderCancelled → CANCELLED
        put(map, LifecycleState.PENDING_SUBMIT, OrderEvent.EventType.CANCELLED, LifecycleState.CANCELLED);
        // PENDING_SUBMIT → CancelRequested → CANCEL_PENDING
        put(map, LifecycleState.PENDING_SUBMIT, OrderEvent.EventType.CANCEL_REQUESTED, LifecycleState.CANCEL_PENDING);

        // SUBMITTED → OrderPartiallyFilled → PARTIALLY_FILLED
        put(map, LifecycleState.SUBMITTED, OrderEvent.EventType.PARTIALLY_FILLED, LifecycleState.PARTIALLY_FILLED);
        // SUBMITTED → OrderFullyFilled → FILLED
        put(map, LifecycleState.SUBMITTED, OrderEvent.EventType.FULLY_FILLED, LifecycleState.FILLED);
        // SUBMITTED → OrderCancelled → CANCELLED
        put(map, LifecycleState.SUBMITTED, OrderEvent.EventType.CANCELLED, LifecycleState.CANCELLED);
        // SUBMITTED → OrderRejected → REJECTED
        put(map, LifecycleState.SUBMITTED, OrderEvent.EventType.REJECTED, LifecycleState.REJECTED);
        // SUBMITTED → OrderExpired → EXPIRED
        put(map, LifecycleState.SUBMITTED, OrderEvent.EventType.EXPIRED, LifecycleState.EXPIRED);
        // SUBMITTED → CancelRequested → CANCEL_PENDING
        put(map, LifecycleState.SUBMITTED, OrderEvent.EventType.CANCEL_REQUESTED, LifecycleState.CANCEL_PENDING);

        // PARTIALLY_FILLED → OrderPartiallyFilled → PARTIALLY_FILLED (additional fill)
        put(map, LifecycleState.PARTIALLY_FILLED, OrderEvent.EventType.PARTIALLY_FILLED, LifecycleState.PARTIALLY_FILLED);
        // PARTIALLY_FILLED → OrderFullyFilled → FILLED
        put(map, LifecycleState.PARTIALLY_FILLED, OrderEvent.EventType.FULLY_FILLED, LifecycleState.FILLED);
        // PARTIALLY_FILLED → OrderCancelled → CANCELLED
        put(map, LifecycleState.PARTIALLY_FILLED, OrderEvent.EventType.CANCELLED, LifecycleState.CANCELLED);
        // PARTIALLY_FILLED → OrderRejected → REJECTED
        put(map, LifecycleState.PARTIALLY_FILLED, OrderEvent.EventType.REJECTED, LifecycleState.REJECTED);
        // PARTIALLY_FILLED → OrderExpired → EXPIRED
        put(map, LifecycleState.PARTIALLY_FILLED, OrderEvent.EventType.EXPIRED, LifecycleState.EXPIRED);
        // PARTIALLY_FILLED → CancelRequested → CANCEL_PENDING
        put(map, LifecycleState.PARTIALLY_FILLED, OrderEvent.EventType.CANCEL_REQUESTED, LifecycleState.CANCEL_PENDING);

        // CANCEL_PENDING → OrderCancelled → CANCELLED
        put(map, LifecycleState.CANCEL_PENDING, OrderEvent.EventType.CANCELLED, LifecycleState.CANCELLED);
        // CANCEL_PENDING → OrderRejected → REJECTED (cancel failed)
        put(map, LifecycleState.CANCEL_PENDING, OrderEvent.EventType.REJECTED, LifecycleState.REJECTED);
        // CANCEL_PENDING → OrderFullyFilled → FILLED (fill before cancel)
        put(map, LifecycleState.CANCEL_PENDING, OrderEvent.EventType.FULLY_FILLED, LifecycleState.FILLED);
        // CANCEL_PENDING → OrderPartiallyFilled → CANCEL_PENDING (partial before cancel)
        put(map, LifecycleState.CANCEL_PENDING, OrderEvent.EventType.PARTIALLY_FILLED, LifecycleState.CANCEL_PENDING);
        // CANCEL_PENDING → OrderExpired → EXPIRED
        put(map, LifecycleState.CANCEL_PENDING, OrderEvent.EventType.EXPIRED, LifecycleState.EXPIRED);

        return Collections.unmodifiableMap(map);
    }

    private static void put(Map<StateTransition, LifecycleState> map,
                            LifecycleState from, OrderEvent.EventType event, LifecycleState to) {
        map.put(new StateTransition(from, event), to);
    }


}
