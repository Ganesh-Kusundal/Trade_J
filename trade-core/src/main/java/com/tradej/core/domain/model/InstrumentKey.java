package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;

public record InstrumentKey(String symbol, ExchangeSegment exchangeSegment) {
}
