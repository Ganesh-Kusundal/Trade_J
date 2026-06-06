package com.tradej.core.domain.model;

import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.InstrumentType;
import com.tradej.core.domain.value.OptionType;
import java.time.LocalDate;

public record Instrument(
        String symbol,
        String canonicalSymbol,
        Exchange exchange,
        ExchangeSegment exchangeSegment,
        String instrumentType,
        String underlying,
        LocalDate expiry,
        Long strikePricePaisa,
        OptionType optionType,
        long lotSize,
        long tickSizePaisa
) {
    public InstrumentKey key() {
        return new InstrumentKey(canonicalSymbol, exchangeSegment);
    }

    public boolean isOption() {
        return optionType != null && optionType != OptionType.UNKNOWN;
    }

    public boolean isFuture() {
        return instrumentType != null && instrumentType.toUpperCase().startsWith("FUT");
    }

    public InstrumentType instrumentTypeEnum() {
        return InstrumentType.parse(instrumentType);
    }
}
