package com.tradej.core.domain.model;

public record OptionQuote(
        Instrument instrument,
        long ltpPaisa,
        long openInterest,
        long volume,
        long bestBidPricePaisa,
        long bestBidQuantity,
        long bestAskPricePaisa,
        long bestAskQuantity,
        OptionGreeks greeks
) {
}
