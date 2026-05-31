package com.tradej.scanner.engine;

import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;

public record ScanDependencies(
        InstrumentResolver instrumentResolver,
        MarketDataProvider marketDataProvider,
        OptionsProvider optionsProvider,
        FuturesProvider futuresProvider
) {
}
