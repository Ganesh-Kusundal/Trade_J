package com.tradej.broker.api.model;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

public record MarketSubscriptionRequest(
        String symbol,
        ExchangeSegment exchangeSegment
) {
    public InstrumentKey key() {
        return InstrumentKey.of(symbol, exchangeSegment);
    }
}
