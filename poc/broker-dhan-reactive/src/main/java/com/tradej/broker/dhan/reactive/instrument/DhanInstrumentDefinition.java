package com.tradej.broker.dhan.reactive.instrument;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;

import com.tradej.core.domain.value.OptionType;

import java.time.LocalDate;

/**
 * Dhan instrument definition containing exchange and security details.
 */
public record DhanInstrumentDefinition(
    String exchangeSegment,
    String securityId,
    String tradingSymbol
) {
    
    public Instrument toInstrument() {
        ExchangeSegment segment = ExchangeSegment.fromCode(exchangeSegment);
        return new Instrument(
            tradingSymbol,
            tradingSymbol,
            segment.exchange(),
            segment,
            "",
            "",
            null,
            null,
            OptionType.UNKNOWN,
            1L,
            5L
        );
    }
}
