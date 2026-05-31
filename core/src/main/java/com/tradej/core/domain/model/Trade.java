package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;

public record Trade(
        String tradeId,
        String orderId,
        String symbol,
        ExchangeSegment exchangeSegment,
        Side side,
        long quantity,
        long pricePaisa,
        long exchangeTimeMs
) {
}
