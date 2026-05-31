package com.tradej.scanner.option;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.scanner.model.OptionScanSpec;

import java.time.LocalDate;

public record OptionScanRequest(
        String underlying,
        ExchangeSegment exchangeSegment,
        OptionScanSpec spec
) {
    public OptionScanRequest {
        if (underlying == null || underlying.isBlank()) {
            throw new IllegalArgumentException("underlying is required");
        }
        if (exchangeSegment == null) {
            throw new IllegalArgumentException("exchangeSegment is required");
        }
        if (spec == null) {
            spec = OptionScanSpec.defaults();
        }
    }

    public static OptionScanRequest of(
            String underlying,
            ExchangeSegment exchangeSegment,
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
        return new OptionScanRequest(underlying, exchangeSegment, spec);
    }
}
