package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;

public record SliceOrderRequest(
        String symbol,
        ExchangeSegment exchangeSegment,
        Side side,
        long quantity,
        OrderType orderType,
        long pricePaisa,
        long triggerPricePaisa,
        ProductType productType,
        Validity validity,
        boolean afterMarketOrder,
        String correlationId
) {
}
