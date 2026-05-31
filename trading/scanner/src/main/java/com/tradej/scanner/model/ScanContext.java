package com.tradej.scanner.model;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Quote;

import java.util.List;

public record ScanContext(
        ScanAsset asset,
        Quote quote,
        OptionChainSnapshot optionChain,
        List<Candle> intradayCandles
) {
    public ScanContext {
        intradayCandles = intradayCandles == null ? List.of() : List.copyOf(intradayCandles);
    }

    public boolean hasValidQuote() {
        return quote != null && quote.ltpPaisa() > 0L;
    }
}
