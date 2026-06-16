package com.tradej.core.domain.oms;

import com.tradej.core.testsupport.TestSymbols;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class OrderStateMachinePropertyTest {

    private static final String ORDER_ID = "ORD-PROP";
    private static final String SYMBOL = TestSymbols.SBIN;

    @Test
    void randomValidFillSequencesRespectInvariants() {
        Random random = new Random(42L);
        for (int trial = 0; trial < 200; trial++) {
            long totalQty = 50 + random.nextInt(200);
            OrderStateMachine machine = new OrderStateMachine(ORDER_ID, SYMBOL, totalQty);
            machine.on(OrderSubmitted.event(ORDER_ID));
            machine.on(OrderAcknowledged.event(ORDER_ID, "EX-" + trial));

            List<OrderEvent> events = new ArrayList<>();
            events.add(OrderSubmitted.event(ORDER_ID));
            events.add(OrderAcknowledged.event(ORDER_ID, "EX-" + trial));

            long reportedFilled = 0L;
            int partialEvents = random.nextInt(4);
            for (int i = 0; i < partialEvents; i++) {
                long slice = 1 + random.nextInt(20);
                reportedFilled += slice;
                if (reportedFilled >= totalQty) {
                    break;
                }
                OrderPartiallyFilled partial = OrderPartiallyFilled.event(ORDER_ID, slice, 100_00L + i);
                events.add(partial);
                machine.on(partial);
                assertInvariants(machine, totalQty);
            }

            long finalReport = Math.min(totalQty, Math.max(machine.toProjection().filledQuantity(), reportedFilled));
            OrderFullyFilled terminal = OrderFullyFilled.event(ORDER_ID, finalReport, 101_00L);
            events.add(terminal);
            machine.on(terminal);
            assertInvariants(machine, totalQty);

            OrderStateMachine replayed = OrderStateMachine.replay(ORDER_ID, SYMBOL, totalQty, events);
            assertEquals(machine.toProjection().filledQuantity(), replayed.toProjection().filledQuantity());
            assertEquals(machine.toProjection().averagePricePaisa(), replayed.toProjection().averagePricePaisa());
            assertEquals(machine.toProjection().status(), replayed.toProjection().status());
        }
    }

    private static void assertInvariants(OrderStateMachine machine, long totalQty) {
        assertTrue(machine.toProjection().filledQuantity() >= 0L);
        assertTrue(machine.toProjection().filledQuantity() <= totalQty);
        assertTrue(machine.toProjection().averagePricePaisa() >= 0L);
    }
}
