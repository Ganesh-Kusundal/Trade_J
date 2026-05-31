package com.tradej.broker.icici.instrument;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

public record BreezeInstrumentDefinition(
        String breezeStockCode,
        String tradingSymbol,
        ExchangeSegment exchangeSegment,
        String token,
        String scriptCode,
        String exchangeCode
) {
    /**
     * Breeze API wire stock code (ShortName), e.g. {@code RELIND}.
     */
    public String stockCode() {
        return breezeStockCode;
    }

    public Instrument toInstrument() {
        Exchange exchange = switch (exchangeSegment) {
            case NSE_EQ -> Exchange.NSE;
            case NSE_FNO -> Exchange.NFO;
            default -> Exchange.UNKNOWN;
        };
        String instrumentType = exchangeSegment == ExchangeSegment.NSE_FNO ? "FNO" : "EQ";
        return new Instrument(
                tradingSymbol,
                tradingSymbol,
                exchange,
                exchangeSegment,
                instrumentType,
                tradingSymbol,
                null,
                null,
                OptionType.UNKNOWN,
                1L,
                1L
        );
    }

    public InstrumentKey toInstrumentKey() {
        return new InstrumentKey(tradingSymbol, exchangeSegment);
    }
}
