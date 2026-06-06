package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderId;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Symbol;

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
    public OrderId orderIdValue() {
        return new OrderId(orderId);
    }

    public Symbol symbolValue() {
        return new Symbol(symbol);
    }
}
