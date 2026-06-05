package com.tradej.app.api.dto;

import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;

public record OrderResponse(
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
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.orderId(),
                order.correlationId(),
                order.symbol(),
                order.exchangeSegment(),
                order.side(),
                order.productType(),
                order.orderType(),
                order.status(),
                order.quantity(),
                order.filledQuantity(),
                order.pricePaisa(),
                order.triggerPricePaisa(),
                order.exchangeTimeMs(),
                order.rejectionReason());
    }
}
