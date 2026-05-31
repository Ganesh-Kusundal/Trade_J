package com.tradej.core.domain.model;

import java.time.LocalDate;
import java.util.List;

public record OptionChainSnapshot(
        Instrument underlying,
        LocalDate expiry,
        long spotPricePaisa,
        List<OptionChainEntry> strikes
) {
}
