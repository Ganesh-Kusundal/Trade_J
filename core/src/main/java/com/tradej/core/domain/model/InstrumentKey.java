package com.tradej.core.domain.model;

import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.value.ExchangeSegment;

public record InstrumentKey(String symbol, ExchangeSegment exchangeSegment) {
    public static InstrumentKey of(String symbol, ExchangeSegment exchangeSegment) {
        return new InstrumentKey(ContractSymbolNormalizer.normalize(symbol), exchangeSegment);
    }
}
