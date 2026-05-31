package com.tradej.core.domain.model;

public record OptionChainEntry(
        long strikePricePaisa,
        OptionQuote call,
        OptionQuote put
) {
}
