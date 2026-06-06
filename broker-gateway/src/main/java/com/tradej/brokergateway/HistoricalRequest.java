package com.tradej.brokergateway;

import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;

/**
 * Structured request for historical candle data.
 */
public record HistoricalRequest(
        String symbol,
        ExchangeSegment segment,
        String interval,
        LocalDate from,
        LocalDate to
) {
}
