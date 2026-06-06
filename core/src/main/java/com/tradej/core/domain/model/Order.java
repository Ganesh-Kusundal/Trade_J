package com.tradej.core.domain.model;

import com.tradej.core.domain.value.CorrelationId;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderId;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Symbol;

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
    public OrderId orderIdValue() {
        return new OrderId(orderId);
    }

    public CorrelationId correlationIdValue() {
        return correlationId != null && !correlationId.isBlank()
                ? new CorrelationId(correlationId) : null;
    }

    public Symbol symbolValue() {
        return new Symbol(symbol);
    }
}
