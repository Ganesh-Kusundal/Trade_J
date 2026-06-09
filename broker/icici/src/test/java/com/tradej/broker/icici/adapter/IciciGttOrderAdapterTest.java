package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IciciGttOrderAdapter}.
 * 
 * <p>Verifies that the adapter correctly throws {@link UnsupportedOperationException}
 * with informative messages for all operations, since ICICI Breeze API does not
 * support GTT (Good-Till-Triggered) orders.
 */
@Tag("unit")
class IciciGttOrderAdapterTest {

    private final GttOrderProvider adapter = new IciciGttOrderAdapter();

    @Test
    @DisplayName("placeForeverOrder throws UnsupportedOperationException with helpful message")
    void placeForeverOrder_shouldThrow() {
        OrderRequest request = new OrderRequest(
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                10,
                OrderType.LIMIT,
                250000L,
                245000L,
                ProductType.INTRADAY,
                Validity.DAY,
                "test-correlation"
        );

        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> adapter.placeForeverOrder(request, "SINGLE", null, null, null)
        );

        assertTrue(ex.getMessage().contains("ICICI does not support GTT orders"),
                "Exception message should explain the API limitation");
    }

    @Test
    @DisplayName("modifyForeverOrder throws UnsupportedOperationException")
    void modifyForeverOrder_shouldThrow() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> adapter.modifyForeverOrder("gtt-123", "SINGLE", "ENTRY", 10, 250000L, 245000L)
        );

        assertTrue(ex.getMessage().contains("ICICI does not support GTT orders"));
    }

    @Test
    @DisplayName("cancelForeverOrder throws UnsupportedOperationException")
    void cancelForeverOrder_shouldThrow() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> adapter.cancelForeverOrder("gtt-123")
        );

        assertTrue(ex.getMessage().contains("ICICI does not support GTT orders"));
    }

    @Test
    @DisplayName("getForeverOrders returns empty list")
    void getForeverOrders_shouldReturnEmpty() {
        assertEquals(0, adapter.getForeverOrders().size(),
                "getForeverOrders should return empty list when API doesn't support GTT orders");
    }
}
