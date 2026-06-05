package com.tradej.app.api.dto;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;

public record PlaceOrderRequest(
        String symbol,
        ExchangeSegment exchangeSegment,
        Side side,
        long quantity,
        OrderType orderType,
        long pricePaisa,
        Long triggerPricePaisa,
        ProductType productType,
        Validity validity,
        String correlationId
) {
    public long effectiveTriggerPricePaisa() {
        return triggerPricePaisa == null ? 0L : triggerPricePaisa;
    }
}
