package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;

public record Position(
        String symbol,
        ExchangeSegment exchangeSegment,
        Side side,
        long quantity,
        long averagePricePaisa,
        long lastPricePaisa,
        long unrealizedPnlPaisa
) {
}
