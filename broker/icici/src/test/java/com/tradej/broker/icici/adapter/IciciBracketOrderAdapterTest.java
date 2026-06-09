package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.BracketOrderProvider;
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
 * Tests for {@link IciciBracketOrderAdapter}.
 * 
 * <p>Verifies that the adapter correctly throws {@link UnsupportedOperationException}
 * with informative messages for all operations, since ICICI Breeze API does not
 * support bracket orders.
 */
@Tag("unit")
class IciciBracketOrderAdapterTest {

    private final BracketOrderProvider adapter = new IciciBracketOrderAdapter();

    @Test
    @DisplayName("placeSuperOrder throws UnsupportedOperationException with helpful message")
    void placeSuperOrder_shouldThrow() {
        OrderRequest request = new OrderRequest(
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                10,
                OrderType.LIMIT,
                250000L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "test-correlation"
        );

        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> adapter.placeSuperOrder(request, 260000L, 240000L, 1000L)
        );

        assertTrue(ex.getMessage().contains("ICICI does not support bracket orders"),
                "Exception message should explain the API limitation");
    }

    @Test
    @DisplayName("modifySuperOrder throws UnsupportedOperationException")
    void modifySuperOrder_shouldThrow() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> adapter.modifySuperOrder("order-123", "ENTRY", 10, 250000L, 240000L)
        );

        assertTrue(ex.getMessage().contains("ICICI does not support bracket orders"));
    }

    @Test
    @DisplayName("cancelSuperOrder throws UnsupportedOperationException")
    void cancelSuperOrder_shouldThrow() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> adapter.cancelSuperOrder("order-123", "ENTRY")
        );

        assertTrue(ex.getMessage().contains("ICICI does not support bracket orders"));
    }

    @Test
    @DisplayName("getSuperOrders returns empty list")
    void getSuperOrders_shouldReturnEmpty() {
        assertEquals(0, adapter.getSuperOrders().size(),
                "getSuperOrders should return empty list when API doesn't support bracket orders");
    }
}
