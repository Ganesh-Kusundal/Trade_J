package com.tradej.broker.icici.websocket;

import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.core.domain.event.*;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.value.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Tag("unit")
class BreezeWebSocketDedupTest {

    private BreezeWebSocketMultiplexer multiplexer;
    private Method isDuplicateMethod;
    private Method extractOrderIdMethod;
    private Method extractStatusMethod;

    @BeforeEach
    void setUp() throws Exception {
        BreezeTokenProvider tokenProvider = mock(BreezeTokenProvider.class);
        BreezeInstrumentResolver instrumentResolver = mock(BreezeInstrumentResolver.class);
        EventMetadataFactory metadataFactory = mock(EventMetadataFactory.class);
        ExecutorService executor = mock(ExecutorService.class);

        multiplexer = new BreezeWebSocketMultiplexer(
                tokenProvider, instrumentResolver, metadataFactory, null, executor
        );

        isDuplicateMethod = BreezeWebSocketMultiplexer.class
                .getDeclaredMethod("isDuplicateOrderEvent", OrderUpdateEvent.class);
        isDuplicateMethod.setAccessible(true);

        extractOrderIdMethod = BreezeWebSocketMultiplexer.class
                .getDeclaredMethod("extractOrderId", OrderUpdateEvent.class);
        extractOrderIdMethod.setAccessible(true);

        extractStatusMethod = BreezeWebSocketMultiplexer.class
                .getDeclaredMethod("extractStatus", OrderUpdateEvent.class);
        extractStatusMethod.setAccessible(true);
    }

    private Order order(String orderId) {
        return new Order(orderId, null, "RELIANCE", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.CNC, OrderType.LIMIT, OrderStatus.OPEN,
                100, 0, 250000, 0, System.currentTimeMillis(), "");
    }

    private EventMetadata metadata() {
        return EventMetadata.root();
    }

    // ── isDuplicateOrderEvent tests ─────────────────────────────────────

    @Test
    void isDuplicateReturnsFalseForFirstEventWithNewOrderId() throws Exception {
        OrderAccepted event = new OrderAccepted(metadata(), order("ORD-001"));

        boolean result = (boolean) isDuplicateMethod.invoke(multiplexer, event);

        assertFalse(result);
    }

    @Test
    void isDuplicateReturnsTrueForSameOrderIdAndSameStatus() throws Exception {
        OrderAccepted event1 = new OrderAccepted(metadata(), order("ORD-002"));
        OrderAccepted event2 = new OrderAccepted(metadata(), order("ORD-002"));

        // First event — not a duplicate
        assertFalse((boolean) isDuplicateMethod.invoke(multiplexer, event1));
        // Second event with same orderId + same status — duplicate
        assertTrue((boolean) isDuplicateMethod.invoke(multiplexer, event2));
    }

    @Test
    void isDuplicateReturnsFalseForSameOrderIdButDifferentStatus() throws Exception {
        Order order = order("ORD-003");
        OrderAccepted accepted = new OrderAccepted(metadata(), order);
        OrderFilled filled = new OrderFilled(metadata(), order, List.of());

        // First event (OPEN) — not a duplicate
        assertFalse((boolean) isDuplicateMethod.invoke(multiplexer, accepted));
        // Second event (TRADED) — different status, so NOT a duplicate (status transition)
        assertFalse((boolean) isDuplicateMethod.invoke(multiplexer, filled));
    }

    @Test
    void isDuplicateReturnsFalseForNullOrderId() throws Exception {
        Order nullIdOrder = new Order(null, null, "RELIANCE", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.CNC, OrderType.LIMIT, OrderStatus.OPEN,
                100, 0, 250000, 0, System.currentTimeMillis(), "");
        OrderAccepted event = new OrderAccepted(metadata(), nullIdOrder);

        boolean result = (boolean) isDuplicateMethod.invoke(multiplexer, event);

        assertFalse(result);
    }

    // ── extractOrderId tests ────────────────────────────────────────────

    @Test
    void extractOrderIdWorksForOrderAccepted() throws Exception {
        OrderAccepted event = new OrderAccepted(metadata(), order("ACC-001"));
        String orderId = (String) extractOrderIdMethod.invoke(null, event);
        assertEquals("ACC-001", orderId);
    }

    @Test
    void extractOrderIdWorksForOrderFilled() throws Exception {
        OrderFilled event = new OrderFilled(metadata(), order("FIL-001"), List.of());
        String orderId = (String) extractOrderIdMethod.invoke(null, event);
        assertEquals("FIL-001", orderId);
    }

    @Test
    void extractOrderIdWorksForOrderRejected() throws Exception {
        OrderRejected event = new OrderRejected(metadata(), order("REJ-001"), "insufficient margin");
        String orderId = (String) extractOrderIdMethod.invoke(null, event);
        assertEquals("REJ-001", orderId);
    }

    @Test
    void extractOrderIdWorksForOrderCancelled() throws Exception {
        OrderCancelled event = new OrderCancelled(metadata(), order("CAN-001"), "user cancelled");
        String orderId = (String) extractOrderIdMethod.invoke(null, event);
        assertEquals("CAN-001", orderId);
    }

    @Test
    void extractOrderIdWorksForOrderModified() throws Exception {
        OrderModified event = new OrderModified(metadata(), order("MOD-001"));
        String orderId = (String) extractOrderIdMethod.invoke(null, event);
        assertEquals("MOD-001", orderId);
    }

    @Test
    void extractOrderIdWorksForOrderPartiallyFilled() throws Exception {
        OrderPartiallyFilled event = new OrderPartiallyFilled(metadata(), order("PART-001"), List.of());
        String orderId = (String) extractOrderIdMethod.invoke(null, event);
        assertEquals("PART-001", orderId);
    }

    // ── extractStatus tests ─────────────────────────────────────────────

    @Test
    void extractStatusReturnsOpenForOrderAccepted() throws Exception {
        OrderAccepted event = new OrderAccepted(metadata(), order("X"));
        OrderStatus status = (OrderStatus) extractStatusMethod.invoke(null, event);
        assertEquals(OrderStatus.OPEN, status);
    }

    @Test
    void extractStatusReturnsTradedForOrderFilled() throws Exception {
        OrderFilled event = new OrderFilled(metadata(), order("X"), List.of());
        OrderStatus status = (OrderStatus) extractStatusMethod.invoke(null, event);
        assertEquals(OrderStatus.TRADED, status);
    }

    @Test
    void extractStatusReturnsPartTradedForOrderPartiallyFilled() throws Exception {
        OrderPartiallyFilled event = new OrderPartiallyFilled(metadata(), order("X"), List.of());
        OrderStatus status = (OrderStatus) extractStatusMethod.invoke(null, event);
        assertEquals(OrderStatus.PART_TRADED, status);
    }

    @Test
    void extractStatusReturnsRejectedForOrderRejected() throws Exception {
        OrderRejected event = new OrderRejected(metadata(), order("X"), "reason");
        OrderStatus status = (OrderStatus) extractStatusMethod.invoke(null, event);
        assertEquals(OrderStatus.REJECTED, status);
    }

    @Test
    void extractStatusReturnsCancelledForOrderCancelled() throws Exception {
        OrderCancelled event = new OrderCancelled(metadata(), order("X"), "reason");
        OrderStatus status = (OrderStatus) extractStatusMethod.invoke(null, event);
        assertEquals(OrderStatus.CANCELLED, status);
    }

    @Test
    void extractStatusReturnsOpenForOrderModified() throws Exception {
        OrderModified event = new OrderModified(metadata(), order("X"));
        OrderStatus status = (OrderStatus) extractStatusMethod.invoke(null, event);
        assertEquals(OrderStatus.OPEN, status);
    }
}
