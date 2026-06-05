package com.tradej.broker.dhan.mapper;

import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class DhanFieldMapperTest {
    @Test
    void mapsForeverOrderFlagsToLimitOrderType() {
        assertEquals(OrderType.LIMIT, DhanFieldMapper.orderType("SINGLE"));
        assertEquals(OrderType.LIMIT, DhanFieldMapper.orderType("OCO"));
    }

    @Test
    void mapsConfirmStatusToPending() {
        assertEquals(OrderStatus.PENDING, DhanFieldMapper.orderStatus("CONFIRM"));
        assertEquals(OrderStatus.PENDING, DhanFieldMapper.orderStatus("TRANSIT"));
    }

    @Test
    void mapsTransactionSide() {
        assertEquals(Side.BUY, DhanFieldMapper.side("BUY"));
        assertEquals(Side.SELL, DhanFieldMapper.side("SHORT"));
    }
}
