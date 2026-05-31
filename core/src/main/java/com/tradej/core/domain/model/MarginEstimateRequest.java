package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;

public record MarginEstimateRequest(
        String symbol,
        ExchangeSegment exchangeSegment,
        Side side,
        long quantity,
        ProductType productType,
        OrderType orderType,
        long pricePaisa,
        long triggerPricePaisa
) {
}
