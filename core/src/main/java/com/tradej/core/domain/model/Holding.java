package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;

public record Holding(
        String symbol,
        ExchangeSegment exchangeSegment,
        long totalQuantity,
        long availableQuantity,
        long collateralQuantity,
        long averagePricePaisa
) {
}
