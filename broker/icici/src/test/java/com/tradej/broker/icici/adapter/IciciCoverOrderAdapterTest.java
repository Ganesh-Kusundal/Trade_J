package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.CoverOrderProvider;
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
 * Tests for {@link IciciCoverOrderAdapter}.
 * 
 * <p>Verifies that the adapter correctly throws {@link UnsupportedOperationException}
 * with informative messages for all operations, since ICICI Breeze API does not
 * support cover orders.
 */
@Tag("unit")
class IciciCoverOrderAdapterTest {

    private final CoverOrderProvider adapter = new IciciCoverOrderAdapter();

    @Test
    @DisplayName("placeCoverOrder throws UnsupportedOperationException with helpful message")
    void placeCoverOrder_shouldThrow() {
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
                () -> adapter.placeCoverOrder(request, 240000L)
        );

        assertTrue(ex.getMessage().contains("ICICI does not support cover orders"),
                "Exception message should explain the API limitation");
    }

    @Test
    @DisplayName("exitCoverOrder throws UnsupportedOperationException")
    void exitCoverOrder_shouldThrow() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> adapter.exitCoverOrder("order-123")
        );

        assertTrue(ex.getMessage().contains("ICICI does not support cover orders"));
    }
}
