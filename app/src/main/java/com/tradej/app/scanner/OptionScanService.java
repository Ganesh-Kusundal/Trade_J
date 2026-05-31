package com.tradej.app.scanner;

import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.scanner.model.OptionScanSpec;
import com.tradej.scanner.option.OptionExpiryPolicy;
import com.tradej.scanner.option.OptionLiquidityScanner;
import com.tradej.scanner.option.OptionScanRequest;
import com.tradej.scanner.option.OptionScanResult;
import com.tradej.scanner.option.OptionSideFilter;

import java.time.LocalDate;
import java.util.List;

public final class OptionScanService {
    private final OptionLiquidityScanner scanner;
    private final OptionsProvider optionsProvider;

    public OptionScanService(OptionsProvider optionsProvider) {
        this.optionsProvider = optionsProvider;
        this.scanner = new OptionLiquidityScanner(optionsProvider);
    }

    public OptionScanResult scan(
            String underlying,
            ExchangeSegment segment,
            OptionExpiryPolicy expiryPolicy,
            LocalDate explicitExpiry,
            OptionSideFilter sides,
            long minOpenInterest,
            long minVolume,
            double maxSpreadBps,
            boolean strictSpread,
            int topN
    ) {
        OptionScanSpec spec = new OptionScanSpec(
                expiryPolicy,
                explicitExpiry,
                sides,
                minOpenInterest,
                minVolume,
                maxSpreadBps,
                strictSpread,
                topN,
                0
        );
        return scanner.scan(new OptionScanRequest(underlying, segment, spec));
    }

    public List<LocalDate> expiries(String underlying, ExchangeSegment segment) {
        return optionsProvider.getExpiries(underlying, segment);
    }
}
