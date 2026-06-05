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
        assertEquals(LifecycleState.NEW, oms.currentStatus());

        oms.on(OrderSubmitted.event(ORDER_ID));
        assertEquals(LifecycleState.PENDING_SUBMIT, oms.currentStatus());
    }

    @Test
    void proceedsFromPendingSubmitToSubmitted() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        oms.on(OrderSubmitted.event(ORDER_ID));
        oms.on(OrderAcknowledged.event(ORDER_ID, "EX-001"));
        assertEquals(LifecycleState.SUBMITTED, oms.currentStatus());
    }

    @Test
    void proceedsThroughFullLifecycle() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        assertEquals(LifecycleState.NEW, oms.currentStatus());

        oms.on(OrderSubmitted.event(ORDER_ID));
        assertEquals(LifecycleState.PENDING_SUBMIT, oms.currentStatus());

        oms.on(OrderAcknowledged.event(ORDER_ID, "EX-001"));
        assertEquals(LifecycleState.SUBMITTED, oms.currentStatus());

        oms.on(OrderPartiallyFilled.event(ORDER_ID, 25, 150_00L));
        assertEquals(LifecycleState.PARTIALLY_FILLED, oms.currentStatus());

        oms.on(OrderPartiallyFilled.event(ORDER_ID, 25, 151_00L));
        assertEquals(LifecycleState.PARTIALLY_FILLED, oms.currentStatus());

        oms.on(OrderFullyFilled.event(ORDER_ID, 100, 150_50L));
        assertEquals(LifecycleState.FILLED, oms.currentStatus());
    }

    // ── Terminal transitions ────────────────────────────────────────────────

    @Test
    void submittedOrderCanBeCancelled() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(OrderCancelled.event(ORDER_ID));
        assertEquals(LifecycleState.CANCELLED, oms.currentStatus());
    }

    @Test
    void submittedOrderCanBeRejected() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(OrderRejected.event(ORDER_ID, "Insufficient margin"));
        assertEquals(LifecycleState.REJECTED, oms.currentStatus());
    }

    @Test
    void submittedOrderCanExpire() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(OrderExpired.event(ORDER_ID));
        assertEquals(LifecycleState.EXPIRED, oms.currentStatus());
    }

    @Test
    void submittedOrderCanBeFilledInOneShot() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(OrderFullyFilled.event(ORDER_ID, TOTAL_QTY, 150_00L));
        assertEquals(LifecycleState.FILLED, oms.currentStatus());
    }

    @Test
    void partiallyFilledOrderCanBeCancelled() {
        OrderStateMachine oms = machineInState(LifecycleState.PARTIALLY_FILLED);
        oms.on(OrderCancelled.event(ORDER_ID));
        assertEquals(LifecycleState.CANCELLED, oms.currentStatus());
    }

    @Test
    void partiallyFilledOrderCanBeRejected() {
        OrderStateMachine oms = machineInState(LifecycleState.PARTIALLY_FILLED);
        oms.on(OrderRejected.event(ORDER_ID, "Exchange rejection"));
        assertEquals(LifecycleState.REJECTED, oms.currentStatus());
    }

    @Test
    void partiallyFilledOrderCanExpire() {
        OrderStateMachine oms = machineInState(LifecycleState.PARTIALLY_FILLED);
        oms.on(OrderExpired.event(ORDER_ID));
        assertEquals(LifecycleState.EXPIRED, oms.currentStatus());
    }

    // ── Cancel pending flow ─────────────────────────────────────────────────

    @Test
    void newOrderCanRequestCancel() {
        OrderStateMachine oms = new OrderStateMachine(ORDER_ID, SYMBOL, TOTAL_QTY);
        oms.on(CancelRequested.event(ORDER_ID));
        assertEquals(LifecycleState.CANCEL_PENDING, oms.currentStatus());
    }

    @Test
    void submittedOrderCanRequestCancel() {
        OrderStateMachine oms = machineInState(LifecycleState.SUBMITTED);
        oms.on(CancelRequested.event(ORDER_ID));
        assertEquals(LifecycleState.CANCEL_PENDING, oms.currentStatus());
    }

    @Test
    void cancelPendingThenConfirmed() {
        OrderStateMachine oms = machineInState(LifecycleState.CANCEL_PENDING);
        oms.on(OrderCancelled.event(ORDER_ID));
        assertEquals(LifecycleState.CANCELLED, oms.currentStatus());
    }

    @Test
    void cancelPendingThenRejected() {
        OrderStateMachine oms = machineInState(LifecycleState.CANCEL_PENDING);
        oms.on(OrderRejected.event(ORDER_ID, "Cancel failed"));
        assertEquals(LifecycleState.REJECTED, oms.currentStatus());
    }

    @Test
    void cancelPendingThenFilled() {
        OrderStateMachine oms = machineInState(LifecycleState.CANCEL_PENDING);
        oms.on(OrderFullyFilled.event(ORDER_ID, TOTAL_QTY, 150_00L));
        assertEquals(LifecycleState.FILLED, oms.currentStatus());
    }

    @Test
    void cancelPendingThenExpired() {
        OrderStateMachine oms = machineInState(LifecycleState.CANCEL_PENDING);
        oms.on(OrderExpired.event(ORDER_ID));
        assertEquals(LifecycleState.EXPIRED, oms.currentStatus());
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
        assertEquals(25, oms.filledQuantity());
        assertEquals(150_00L, oms.averagePricePaisa());

        oms.on(OrderPartiallyFilled.event(ORDER_ID, 25, 152_00L));
        assertEquals(50, oms.filledQuantity());
        // VWAP = (25*15000 + 25*15200) / 50 = (375000 + 380000) / 50 = 755000 / 50 = 15100
        assertEquals(151_00L, oms.averagePricePaisa());

        oms.on(OrderFullyFilled.event(ORDER_ID, 100, 151_00L));
        assertEquals(100, oms.filledQuantity());
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
        assertEquals(TOTAL_QTY, oms.filledQuantity());
        assertEquals(150_50L, oms.averagePricePaisa());
    }

    @Test
    void cumulativeFullyFilledWithNoAdditionalQtyPreservesVwap() {
        OrderStateMachine oms = machineInState(LifecycleState.PARTIALLY_FILLED);
        oms.on(OrderPartiallyFilled.event(ORDER_ID, 75, 152_00L));
        assertEquals(TOTAL_QTY, oms.filledQuantity());
        long vwapBeforeTerminal = oms.averagePricePaisa();

        oms.on(OrderFullyFilled.event(ORDER_ID, TOTAL_QTY, 999_00L));
        assertEquals(TOTAL_QTY, oms.filledQuantity());
        assertEquals(vwapBeforeTerminal, oms.averagePricePaisa());
    }

    @Test
    void cumulativeFullyFilledDoesNotShrinkFilledQuantity() {
        OrderStateMachine oms = machineInState(LifecycleState.PARTIALLY_FILLED);
        oms.on(OrderPartiallyFilled.event(ORDER_ID, 60, 150_00L));
        assertEquals(85, oms.filledQuantity());

        oms.on(OrderFullyFilled.event(ORDER_ID, 50, 140_00L));
        assertEquals(85, oms.filledQuantity());
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
        assertEquals(LifecycleState.FILLED, oms.currentStatus());
        assertEquals(100, oms.filledQuantity());
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
