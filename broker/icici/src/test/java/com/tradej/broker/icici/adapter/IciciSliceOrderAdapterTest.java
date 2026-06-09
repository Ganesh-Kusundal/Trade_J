package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.core.domain.model.SliceOrderRequest;
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
 * Tests for {@link IciciSliceOrderAdapter}.
 * 
 * <p>Verifies that the adapter correctly throws {@link UnsupportedOperationException}
 * with informative message, since ICICI Breeze API does not support slice orders.
 */
@Tag("unit")
class IciciSliceOrderAdapterTest {

    private final SliceOrderCommand adapter = new IciciSliceOrderAdapter();

    @Test
    @DisplayName("placeSliceOrder throws UnsupportedOperationException with helpful message")
    void placeSliceOrder_shouldThrow() {
        SliceOrderRequest request = new SliceOrderRequest(
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                1000,
                OrderType.LIMIT,
                250000L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                false,
                "test-correlation"
        );

        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> adapter.placeSliceOrder(request)
        );

        assertTrue(ex.getMessage().contains("ICICI does not support slice orders"),
                "Exception message should explain the API limitation");
    }
}
