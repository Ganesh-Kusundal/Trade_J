package com.tradej.broker.dhan.instrument;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

import java.time.LocalDate;

public record DhanInstrumentDefinition(
        String symbol,
        String canonicalSymbol,
        Exchange exchange,
        ExchangeSegment exchangeSegment,
        String securityId,
        String instrumentType,
        String underlying,
        LocalDate expiry,
        Long strikePricePaisa,
        OptionType optionType,
        long lotSize,
        long tickSizePaisa,
        String underlyingSecurityId
) {
    public Instrument toInstrument() {
        return new Instrument(
                symbol,
                canonicalSymbol,
                exchange,
                exchangeSegment,
                instrumentType,
                underlying,
                expiry,
                strikePricePaisa,
                optionType,
                lotSize,
                tickSizePaisa
        );
    }

    public InstrumentKey key() {
        return new InstrumentKey(canonicalSymbol, exchangeSegment);
    }

    public boolean isOption() {
        return optionType != null && optionType != OptionType.UNKNOWN;
    }

    public boolean isFuture() {
        return instrumentType != null && instrumentType.toUpperCase().startsWith("FUT");
    }
}
