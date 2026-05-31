package com.tradej.scanner.model;

import com.tradej.scanner.option.OptionExpiryPolicy;
import com.tradej.scanner.option.OptionSideFilter;

import java.time.LocalDate;
import java.util.List;

public record OptionScanSpec(
        OptionExpiryPolicy expiryPolicy,
        LocalDate explicitExpiry,
        OptionSideFilter sides,
        long minOpenInterest,
        long minVolume,
        double maxSpreadBps,
        boolean strictSpread,
        int topNPerUnderlying,
        int topNGlobal
) {
    public OptionScanSpec {
        if (expiryPolicy == null) {
            expiryPolicy = OptionExpiryPolicy.NEAREST;
        }
        if (sides == null) {
            sides = OptionSideFilter.BOTH;
        }
        if (topNPerUnderlying < 0) {
            topNPerUnderlying = 0;
        }
        if (topNGlobal < 0) {
            topNGlobal = 0;
        }
    }

    public static OptionScanSpec defaults() {
        return new OptionScanSpec(
                OptionExpiryPolicy.NEAREST,
                null,
                OptionSideFilter.BOTH,
                1000L,
                0L,
                300.0,
                false,
                10,
                0
        );
    }

    public static OptionScanSpec fromConfig(
            String expiryPolicy,
            String explicitExpiry,
            List<String> sides,
            long minOpenInterest,
            long minVolume,
            double maxSpreadBps,
            boolean strictSpread,
            int topNPerUnderlying,
            int topNGlobal
    ) {
        LocalDate expiry = explicitExpiry == null || explicitExpiry.isBlank()
                ? null
                : LocalDate.parse(explicitExpiry);
        return new OptionScanSpec(
                OptionExpiryPolicy.parse(expiryPolicy),
                expiry,
                OptionSideFilter.fromList(sides),
                minOpenInterest,
                minVolume,
                maxSpreadBps,
                strictSpread,
                topNPerUnderlying,
                topNGlobal
        );
    }
}
