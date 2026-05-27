package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;

public record Order(
        String orderId,
        String correlationId,
        String symbol,
        ExchangeSegment exchangeSegment,
        Side side,
        ProductType productType,
        OrderType orderType,
        OrderStatus status,
        long quantity,
        long filledQuantity,
        long pricePaisa,
        long triggerPricePaisa,
        long exchangeTimeMs,
        String rejectionReason
) {
}
