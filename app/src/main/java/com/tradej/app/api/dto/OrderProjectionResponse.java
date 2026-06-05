package com.tradej.app.api.dto;

import com.tradej.core.domain.oms.LifecycleState;
import com.tradej.core.domain.oms.OrderProjection;

public record OrderProjectionResponse(
        String orderId,
        String symbol,
        long totalQuantity,
        long filledQuantity,
        long averagePricePaisa,
        String status
) {
    public static OrderProjectionResponse from(OrderProjection projection) {
        return new OrderProjectionResponse(
                projection.orderId(),
                projection.symbol(),
                projection.totalQuantity(),
                projection.filledQuantity(),
                projection.averagePricePaisa(),
                projection.status().name());
    }
}
