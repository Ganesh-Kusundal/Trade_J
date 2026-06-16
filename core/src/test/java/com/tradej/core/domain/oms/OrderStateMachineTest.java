package com.tradej.core.domain.oms;

import com.tradej.core.testsupport.TestSymbols;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderStateMachineTest {

    private static OrderSubmitted submitted(String orderId) {
        return OrderSubmitted.event(orderId);
    }

    private static OrderAcknowledged acknowledged(String orderId) {
        return OrderAcknowledged.event(orderId, "EXCH-" + orderId);
    }

    private static OrderPartiallyFilled partiallyFilled(String orderId, long qty, long pricePaisa) {
        return new OrderPartiallyFilled(orderId, qty, pricePaisa);
    }

    private static OrderFullyFilled fullyFilled(String orderId, long totalQty, long pricePaisa) {
        return new OrderFullyFilled(orderId, totalQty, pricePaisa);
    }

    private static CancelRequested cancelRequested(String orderId) {
        return new CancelRequested(orderId);
    }

    private static OrderCancelled cancelled(String orderId) {
        return new OrderCancelled(orderId);
    }

    private static OrderRejected rejected(String orderId) {
        return OrderRejected.event(orderId, "test rejection");
    }

    private static OrderExpired expired(String orderId) {
        return new OrderExpired(orderId);
    }

    @Test
    void happyPath_submitted_acknowledged_filled() {
        OrderStateMachine sm = new OrderStateMachine("ORD-1", TestSymbols.RELIANCE, 100);
        sm.on(submitted("ORD-1"));
        assertEquals(LifecycleState.PENDING_SUBMIT, sm.toProjection().status());

        sm.on(acknowledged("ORD-1"));
        assertEquals(LifecycleState.SUBMITTED, sm.toProjection().status());

        sm.on(fullyFilled("ORD-1", 100, 2500_00L));
        assertEquals(LifecycleState.FILLED, sm.toProjection().status());
        assertEquals(100, sm.toProjection().filledQuantity());
    }

    @Test
    void cancelBeforeAck_cancelPending_thenCancelled() {
        OrderStateMachine sm = new OrderStateMachine("ORD-2", TestSymbols.TCS, 50);
        sm.on(submitted("ORD-2"));
        sm.on(cancelRequested("ORD-2"));
        assertEquals(LifecycleState.CANCEL_PENDING, sm.toProjection().status());

        sm.on(cancelled("ORD-2"));
        assertEquals(LifecycleState.CANCELLED, sm.toProjection().status());
    }

    @Test
    void partialFill_accumulatesCorrectly() {
        OrderStateMachine sm = new OrderStateMachine("ORD-3", TestSymbols.INFY, 100);
        sm.on(submitted("ORD-3"));
        sm.on(acknowledged("ORD-3"));

        sm.on(partiallyFilled("ORD-3", 30, 1500_00L));
        OrderProjection p = sm.toProjection();
        assertEquals(LifecycleState.PARTIALLY_FILLED, p.status());
        assertEquals(30, p.filledQuantity());
        assertEquals(1500_00L, p.averagePricePaisa());
    }

    @Test
    void partialFill_thenFullyFilled() {
        OrderStateMachine sm = new OrderStateMachine("ORD-4", TestSymbols.SBIN, 100);
        sm.on(submitted("ORD-4"));
        sm.on(acknowledged("ORD-4"));

        sm.on(partiallyFilled("ORD-4", 40, 750_00L));
        sm.on(fullyFilled("ORD-4", 100, 755_00L));

        OrderProjection p = sm.toProjection();
        assertEquals(LifecycleState.FILLED, p.status());
        assertEquals(100, p.filledQuantity());
    }

    @Test
    void invalidTransition_throws() {
        OrderStateMachine sm = new OrderStateMachine("ORD-5", TestSymbols.RELIANCE, 100);
        assertThrows(IllegalStateException.class, () -> sm.on(acknowledged("ORD-5")),
                "Cannot acknowledge without submitting first");
    }

    @Test
    void replay_rebuildsStateFromEvents() {
        List<OrderEvent> events = List.of(
                submitted("ORD-6"),
                acknowledged("ORD-6"),
                partiallyFilled("ORD-6", 50, 2000_00L)
        );

        OrderStateMachine sm = OrderStateMachine.replay("ORD-6", "WIPRO", 100, events);
        OrderProjection p = sm.toProjection();
        assertEquals(LifecycleState.PARTIALLY_FILLED, p.status());
        assertEquals(50, p.filledQuantity());
        assertEquals(2000_00L, p.averagePricePaisa());
    }

    @Test
    void vwapCalculation_multiplePartials() {
        OrderStateMachine sm = new OrderStateMachine("ORD-7", "HDFCBANK", 100);
        sm.on(submitted("ORD-7"));
        sm.on(acknowledged("ORD-7"));

        sm.on(partiallyFilled("ORD-7", 40, 1600_00L));
        sm.on(partiallyFilled("ORD-7", 60, 1610_00L));

        OrderProjection p = sm.toProjection();
        assertEquals(100, p.filledQuantity());
        long expectedVwap = (40 * 1600_00L + 60 * 1610_00L) / 100;
        assertEquals(expectedVwap, p.averagePricePaisa());
    }

    @Test
    void expiredFromSubmitted_validTransition() {
        OrderStateMachine sm = new OrderStateMachine("ORD-8", TestSymbols.TCS, 50);
        sm.on(submitted("ORD-8"));
        sm.on(acknowledged("ORD-8"));
        sm.on(expired("ORD-8"));
        assertEquals(LifecycleState.EXPIRED, sm.toProjection().status());
    }

    @Test
    void fillInCancelPending_staysPartiallyFilled() {
        OrderStateMachine sm = new OrderStateMachine("ORD-9", TestSymbols.INFY, 100);
        sm.on(submitted("ORD-9"));
        sm.on(acknowledged("ORD-9"));
        sm.on(partiallyFilled("ORD-9", 30, 1500_00L));
        sm.on(cancelRequested("ORD-9"));
        assertEquals(LifecycleState.CANCEL_PENDING, sm.toProjection().status());

        sm.on(partiallyFilled("ORD-9", 20, 1510_00L));
        assertEquals(LifecycleState.CANCEL_PENDING, sm.toProjection().status(),
                "Partial fill during CANCEL_PENDING should stay CANCEL_PENDING");
        assertEquals(50, sm.toProjection().filledQuantity());
    }

    @Test
    void rejectedFromPendingSubmit_validTransition() {
        OrderStateMachine sm = new OrderStateMachine("ORD-10", TestSymbols.SBIN, 100);
        sm.on(submitted("ORD-10"));
        sm.on(rejected("ORD-10"));
        assertEquals(LifecycleState.REJECTED, sm.toProjection().status());
    }

    @Test
    void wrongOrderId_throws() {
        OrderStateMachine sm = new OrderStateMachine("ORD-11", TestSymbols.RELIANCE, 100);
        assertThrows(IllegalArgumentException.class,
                () -> sm.on(submitted("WRONG-ID")));
    }

    @Test
    void concurrent_sameOrder_synchronized() throws Exception {
        OrderStateMachine sm = new OrderStateMachine("ORD-12", TestSymbols.RELIANCE, 1000);
        sm.on(submitted("ORD-12"));
        sm.on(acknowledged("ORD-12"));

        int threadCount = 4;
        int fillsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < fillsPerThread; i++) {
                        sm.on(partiallyFilled("ORD-12", 1, 2500_00L));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(0, errors.get(), "No exceptions during concurrent fills");

        OrderProjection p = sm.toProjection();
        assertEquals(200, p.filledQuantity(), "All 200 fills (4 threads × 50) must be accumulated");
        assertEquals(2500_00L, p.averagePricePaisa(), "VWAP should be 2500.00 since all fills at same price");
    }
}
