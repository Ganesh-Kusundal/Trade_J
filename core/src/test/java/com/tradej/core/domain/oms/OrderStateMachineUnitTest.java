package com.tradej.core.domain.oms;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderStateMachineUnitTest {

    private static final String ORDER_ID = "ORD-001";
    private static final String SYMBOL = "SBIN";
    private static final long TOTAL_QTY = 100;

    // ── Happy path: full lifecycle ──────────────────────────────────────────

    @Test
    void proceedsFromNewToPendingSubmit() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        assertEquals(LifecycleState.NEW, oms.toProjection().status());

        oms.on(OrderSubmitted.event(ORDER_ID));
        assertEquals(LifecycleState.PENDING_SUBMIT, oms.toProjection().status());
    }

    @Test
    void proceedsFromPendingSubmitToSubmitted() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        oms.on(OrderSubmitted.event(ORDER_ID));
        oms.on(OrderAcknowledged.event(ORDER_ID, "EX-001"));
        assertEquals(LifecycleState.SUBMITTED, oms.toProjection().status());
    }

    @Test
    void proceedsThroughFullLifecycle() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        assertEquals(LifecycleState.NEW, oms.toProjection().status());

        oms.on(OrderSubmitted.event(ORDER_ID));
        assertEquals(LifecycleState.PENDING_SUBMIT, oms.toProjection().status());

        oms.on(OrderAcknowledged.event(ORDER_ID, "EX-001"));
        assertEquals(LifecycleState.SUBMITTED, oms.toProjection().status());

        oms.on(OrderPartiallyFilled.event(ORDER_ID, 25, 150_00L));
        assertEquals(LifecycleState.PARTIALLY_FILLED, oms.toProjection().status());

        oms.on(OrderPartiallyFilled.event(ORDER_ID, 25, 151_00L));
        assertEquals(LifecycleState.PARTIALLY_FILLED, oms.toProjection().status());

        oms.on(OrderFullyFilled.event(ORDER_ID, 100, 150_50L));
        assertEquals(LifecycleState.FILLED, oms.toProjection().status());
    }

    // ── Terminal transitions ────────────────────────────────────────────────

    @Test
    void submittedOrderCanBeCancelled() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(OrderCancelled.event(ORDER_ID));
        assertEquals(LifecycleState.CANCELLED, oms.toProjection().status());
    }

    @Test
    void submittedOrderCanBeRejected() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(OrderRejected.event(ORDER_ID, "Insufficient margin"));
        assertEquals(LifecycleState.REJECTED, oms.toProjection().status());
    }

    @Test
    void submittedOrderCanExpire() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(OrderExpired.event(ORDER_ID));
        assertEquals(LifecycleState.EXPIRED, oms.toProjection().status());
    }

    @Test
    void submittedOrderCanBeFilledInOneShot() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(OrderFullyFilled.event(ORDER_ID, TOTAL_QTY, 150_00L));
        assertEquals(LifecycleState.FILLED, oms.toProjection().status());
    }

    @Test
    void partiallyFilledOrderCanBeCancelled() {
        OrderStateMachine oms = machineInState(LifecycleState.PARTIALLY_FILLED);
        oms.on(OrderCancelled.event(ORDER_ID));
        assertEquals(LifecycleState.CANCELLED, oms.toProjection().status());
    }

    @Test
    void partiallyFilledOrderCanBeRejected() {
        OrderStateMachine oms = machineInState(LifecycleState.PARTIALLY_FILLED);
        oms.on(OrderRejected.event(ORDER_ID, "Exchange rejection"));
        assertEquals(LifecycleState.REJECTED, oms.toProjection().status());
    }

    @Test
    void partiallyFilledOrderCanExpire() {
        OrderStateMachine oms = machineInState(LifecycleState.PARTIALLY_FILLED);
        oms.on(OrderExpired.event(ORDER_ID));
        assertEquals(LifecycleState.EXPIRED, oms.toProjection().status());
    }

    // ── Cancel pending flow ─────────────────────────────────────────────────

    @Test
    void newOrderCanRequestCancel() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        oms.on(CancelRequested.event(ORDER_ID));
        assertEquals(LifecycleState.CANCEL_PENDING, oms.toProjection().status());
    }

    @Test
    void submittedOrderCanRequestCancel() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(CancelRequested.event(ORDER_ID));
        assertEquals(LifecycleState.CANCEL_PENDING, oms.toProjection().status());
    }

    @Test
    void cancelPendingThenConfirmed() {
        OrderStateMachine oms = machineInState(LifecycleState.CANCEL_PENDING);
        oms.on(OrderCancelled.event(ORDER_ID));
        assertEquals(LifecycleState.CANCELLED, oms.toProjection().status());
    }

    @Test
    void cancelPendingThenRejected() {
        OrderStateMachine oms = machineInState(LifecycleState.CANCEL_PENDING);
        oms.on(OrderRejected.event(ORDER_ID, "Cancel failed"));
        assertEquals(LifecycleState.REJECTED, oms.toProjection().status());
    }

    @Test
    void cancelPendingThenFilled() {
        OrderStateMachine oms = machineInState(LifecycleState.CANCEL_PENDING);
        oms.on(OrderFullyFilled.event(ORDER_ID, TOTAL_QTY, 150_00L));
        assertEquals(LifecycleState.FILLED, oms.toProjection().status());
    }

    @Test
    void cancelPendingThenExpired() {
        OrderStateMachine oms = machineInState(LifecycleState.CANCEL_PENDING);
        oms.on(OrderExpired.event(ORDER_ID));
        assertEquals(LifecycleState.EXPIRED, oms.toProjection().status());
    }

    // ── Invalid transitions ─────────────────────────────────────────────────

    @Test
    void cannotFillBeforeSubmit() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        assertThrows(IllegalStateException.class,
                () -> oms.on(OrderFullyFilled.event(ORDER_ID, TOTAL_QTY, 150_00L)));
    }

    @Test
    void cannotTransitionFromFinalState() {
        OrderStateMachine oms = machineInState(LifecycleState.FILLED);
        // Any attempt to transition from FILLED should throw
        assertThrows(IllegalStateException.class,
                () -> oms.on(OrderCancelled.event(ORDER_ID)));
    }

    @Test
    void orderIdMismatchThrows() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        assertThrows(IllegalArgumentException.class,
                () -> oms.on(OrderSubmitted.event("DIFFERENT-ORDER")));
    }

    // ── VWAP / fill tracking ────────────────────────────────────────────────

    @Test
    void tracksFilledQuantityAndVwap() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        oms.on(OrderSubmitted.event(ORDER_ID));
        oms.on(OrderAcknowledged.event(ORDER_ID, "EX-001"));

        oms.on(OrderPartiallyFilled.event(ORDER_ID, 25, 150_00L));
        assertEquals(25, oms.toProjection().filledQuantity());
        assertEquals(150_00L, oms.averagePricePaisa());

        oms.on(OrderPartiallyFilled.event(ORDER_ID, 25, 152_00L));
        assertEquals(50, oms.toProjection().filledQuantity());
        // VWAP = (25*15000 + 25*15200) / 50 = (375000 + 380000) / 50 = 755000 / 50 = 15100
        assertEquals(151_00L, oms.averagePricePaisa());

        oms.on(OrderFullyFilled.event(ORDER_ID, 100, 151_00L));
        assertEquals(100, oms.toProjection().filledQuantity());
    }

    @Test
    void averagePriceIsZeroWhenNoFills() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        assertEquals(0L, oms.averagePricePaisa());
    }

    @Test
    void fullFillSetsCorrectQuantity() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(OrderFullyFilled.event(ORDER_ID, TOTAL_QTY, 150_50L));
        assertEquals(TOTAL_QTY, oms.toProjection().filledQuantity());
        assertEquals(150_50L, oms.averagePricePaisa());
    }

    @Test
    void cumulativeFullyFilledWithNoAdditionalQtyPreservesVwap() {
        OrderStateMachine oms = machineInState(LifecycleState.PARTIALLY_FILLED);
        oms.on(OrderPartiallyFilled.event(ORDER_ID, 75, 152_00L));
        assertEquals(TOTAL_QTY, oms.toProjection().filledQuantity());
        long vwapBeforeTerminal = oms.averagePricePaisa();

        oms.on(OrderFullyFilled.event(ORDER_ID, TOTAL_QTY, 999_00L));
        assertEquals(TOTAL_QTY, oms.toProjection().filledQuantity());
        assertEquals(vwapBeforeTerminal, oms.averagePricePaisa());
    }

    @Test
    void cumulativeFullyFilledDoesNotShrinkFilledQuantity() {
        OrderStateMachine oms = machineInState(LifecycleState.PARTIALLY_FILLED);
        oms.on(OrderPartiallyFilled.event(ORDER_ID, 60, 150_00L));
        assertEquals(85, oms.toProjection().filledQuantity());

        oms.on(OrderFullyFilled.event(ORDER_ID, 50, 140_00L));
        assertEquals(85, oms.toProjection().filledQuantity());
    }

    @Test
    void overfillGuardRejectsExcessiveFilledQuantity() {
        // Use fromProjection to create PARTIALLY_FILLED with filled == total
        // (edge case: race condition where fill and state transition arrive out of order)
        OrderStateMachine oms = OrderStateMachine.fromProjection(
                ORDER_ID, SYMBOL, TOTAL_QTY,
                LifecycleState.PARTIALLY_FILLED, TOTAL_QTY, 150_50L);
        assertEquals(TOTAL_QTY, oms.toProjection().filledQuantity());
        long vwapAtFull = oms.averagePricePaisa();

        // Receive another FullyFilled reporting 120 total (overfill)
        // additional = min(max(100, 120), 100) - 100 = 0
        // Should NOT update VWAP or filledQuantity
        oms.on(OrderFullyFilled.event(ORDER_ID, 120, 999_00L));

        assertEquals(TOTAL_QTY, oms.toProjection().filledQuantity(), "Filled quantity should not exceed total");
        assertEquals(vwapAtFull, oms.averagePricePaisa(), "VWAP should not change on overfill");
        assertEquals(LifecycleState.FILLED, oms.toProjection().status());
    }

    // ── replay() static factory ─────────────────────────────────────────────

    @Test
    void replayFromEventsProducesCorrectState() {
        List<OrderEvent> events = List.of(
                OrderSubmitted.event(ORDER_ID),
                OrderAcknowledged.event(ORDER_ID, "EX-001"),
                OrderPartiallyFilled.event(ORDER_ID, 25, 150_00L),
                OrderFullyFilled.event(ORDER_ID, 100, 150_50L)
        );

        // VWAP = (25×15000 + 75×15050) / 100 = (375000 + 1128750) / 100 = 15037 (integer division)
        OrderStateMachine oms = OrderStateMachine.replay(ORDER_ID, SYMBOL, TOTAL_QTY, events);
        assertEquals(LifecycleState.FILLED, oms.toProjection().status());
        assertEquals(100, oms.toProjection().filledQuantity());
        assertEquals(15037L, oms.averagePricePaisa());
    }

    // ── toProjection() ──────────────────────────────────────────────────────

    @Test
    void projectionReflectsMachineState() {
        OrderStateMachine oms = machineInState(LifecycleState.FILLED);
        OrderProjection proj = oms.toProjection();

        assertEquals(ORDER_ID, proj.orderId());
        assertEquals(SYMBOL, proj.symbol());
        assertEquals(TOTAL_QTY, proj.totalQuantity());
        assertEquals(TOTAL_QTY, proj.filledQuantity());
        assertEquals(LifecycleState.FILLED, proj.status());
    }

    @Test
    void projectionIsFinalForTerminalStates() {
        OrderStateMachine filled = machineInState(LifecycleState.FILLED);
        assertTrue(filled.toProjection().isFinal());
        assertFalse(filled.toProjection().hasOpenPosition());

        OrderStateMachine submitted = machineInState(LifecycleState.SUBMITTED);
        assertFalse(submitted.toProjection().isFinal());
        assertTrue(submitted.toProjection().hasOpenPosition());

        OrderStateMachine cancelled = machineInState(LifecycleState.CANCELLED);
        assertTrue(cancelled.toProjection().isFinal());
    }

    @Test
    void newStateIsNeitherFinalNorOpen() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        OrderProjection proj = oms.toProjection();
        assertFalse(proj.isFinal());
        assertFalse(proj.hasOpenPosition());
    }

    @Test
    void pendingSubmitState() {
        OrderStateMachine oms = machineInState(LifecycleState.PENDING_SUBMIT);
        OrderProjection proj = oms.toProjection();
        assertEquals(LifecycleState.PENDING_SUBMIT, proj.status());
        assertFalse(proj.isFinal());
        assertFalse(proj.hasOpenPosition()); // not submitted yet
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private static OrderStateMachine machineInState(LifecycleState target) {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        switch (target) {
            case NEW -> {}
            case PENDING_SUBMIT -> oms.on(OrderSubmitted.event(ORDER_ID));
            case SUBMITTED -> {
                oms.on(OrderSubmitted.event(ORDER_ID));
                oms.on(OrderAcknowledged.event(ORDER_ID, "EX-001"));
            }
            case PARTIALLY_FILLED -> {
                oms.on(OrderSubmitted.event(ORDER_ID));
                oms.on(OrderAcknowledged.event(ORDER_ID, "EX-001"));
                oms.on(OrderPartiallyFilled.event(ORDER_ID, 25, 150_00L));
            }
            case FILLED -> {
                oms.on(OrderSubmitted.event(ORDER_ID));
                oms.on(OrderAcknowledged.event(ORDER_ID, "EX-001"));
                oms.on(OrderFullyFilled.event(ORDER_ID, TOTAL_QTY, 150_50L));
            }
            case CANCEL_PENDING -> {
                oms.on(OrderSubmitted.event(ORDER_ID));
                oms.on(CancelRequested.event(ORDER_ID));
            }
            case CANCELLED -> {
                oms.on(OrderSubmitted.event(ORDER_ID));
                oms.on(OrderAcknowledged.event(ORDER_ID, "EX-001"));
                oms.on(OrderCancelled.event(ORDER_ID));
            }
            case REJECTED -> {
                oms.on(OrderSubmitted.event(ORDER_ID));
                oms.on(OrderRejected.event(ORDER_ID, "Test reject"));
            }
            case EXPIRED -> {
                oms.on(OrderSubmitted.event(ORDER_ID));
                oms.on(OrderAcknowledged.event(ORDER_ID, "EX-001"));
                oms.on(OrderExpired.event(ORDER_ID));
            }
        }
        return oms;
    }
}
