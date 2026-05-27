package com.tradej.core.domain.model;

import java.time.LocalDate;

public record OptionStrikeSelection(
        LocalDate expiry,
        long strikePricePaisa,
        InstrumentKey call,
        InstrumentKey put
) {
}
