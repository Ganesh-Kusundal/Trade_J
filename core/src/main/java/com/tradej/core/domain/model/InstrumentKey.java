package com.tradej.core.domain.model;

import com.tradej.core.domain.instrument.StandardInstrumentIdentityService;
import com.tradej.core.domain.value.ExchangeSegment;

public record InstrumentKey(String symbol, ExchangeSegment exchangeSegment) {
    public static InstrumentKey of(String symbol, ExchangeSegment exchangeSegment) {
        return StandardInstrumentIdentityService.INSTANCE.key(symbol, exchangeSegment);
    }
}
