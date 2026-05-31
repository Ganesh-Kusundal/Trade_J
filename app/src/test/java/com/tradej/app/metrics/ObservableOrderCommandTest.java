package com.tradej.app.metrics;

import com.tradej.broker.api.port.OrderCommand;
import com.tradej.core.domain.model.*;
import com.tradej.core.domain.value.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ObservableOrderCommandTest {

    private OrderCommand delegate;
    private ObservableOrderCommand observable;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        delegate = mock(OrderCommand.class);
        registry = new SimpleMeterRegistry();
        observable = new ObservableOrderCommand(delegate, registry);
    }

    @Test
    void delegatesPlaceOrderAndRecordsMetrics() {
        OrderRequest request = new OrderRequest(
                "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100,
                OrderType.LIMIT, 750_00L, 0L, ProductType.INTRADAY,
                Validity.DAY, "corr-1"
        );
        Order expected = new Order("ORD-1", "corr-1", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.PENDING,
                100, 0, 750_00L, 0, 0, null);
        when(delegate.placeOrder(request)).thenReturn(expected);

        Order result = observable.placeOrder(request);

        assertSame(expected, result);
        assertEquals(1, registry.counter("dhan.order.calls", "action", "placeOrder").count());
        assertNotNull(registry.find("dhan.order.latency").timer());
    }

    @Test
    void delegatesCancelOrderAndRecordsMetrics() {
        when(delegate.cancelOrder("ORD-1")).thenReturn(true);

        boolean result = observable.cancelOrder("ORD-1");

        assertTrue(result);
        assertEquals(1, registry.counter("dhan.order.calls", "action", "cancelOrder").count());
    }

    @Test
    void delegatesModifyOrder() {
        ModifyOrderRequest request = new ModifyOrderRequest("ORD-1", 200L, 751_00L, 0L, OrderType.LIMIT, Validity.DAY);
        Order expected = new Order("ORD-1", "corr-1", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.PENDING,
                200, 0, 751_00L, 0, 0, null);
        when(delegate.modifyOrder(request)).thenReturn(expected);

        Order result = observable.modifyOrder(request);

        assertSame(expected, result);
        assertEquals(1, registry.counter("dhan.order.calls", "action", "modifyOrder").count());
    }

    @Test
    void delegatesCancelAllAndSquareOff() {
        when(delegate.cancelAllOpenOrders()).thenReturn(List.of());
        when(delegate.cancelAndSquareOffIntradayPositions()).thenReturn(List.of());

        assertNotNull(observable.cancelAllOpenOrders());
        assertNotNull(observable.cancelAndSquareOffIntradayPositions());
    }

    @Test
    void delegatesSetKillSwitch() {
        when(delegate.setKillSwitch(true)).thenReturn(true);

        assertTrue(observable.setKillSwitch(true));
    }
}
