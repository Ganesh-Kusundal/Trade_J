package com.tradej.broker.upstox.instrument;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

import java.time.LocalDate;

/**
 * Raw instrument definition loaded from the Upstox instrument master.
 */
public record UpstoxInstrumentDefinition(
        long instrumentToken,
        String instrumentKey,
        String tradingSymbol,
        String exchangeName,
        ExchangeSegment exchangeSegment,
        String name,
        String isin,
        long lotSize,
        long tickSizePaisa,
        OptionType optionType,
        long strikePricePaisa,
        LocalDate expiry,
        String underlyingKey
) {
    private static Exchange mapExchange(String name) {
        if (name == null) {
            return Exchange.UNKNOWN;
        }
        return switch (name.toUpperCase()) {
            case "NSE" -> Exchange.NSE;
            case "BSE" -> Exchange.BSE;
            case "NFO" -> Exchange.NFO;
            case "BFO" -> Exchange.BFO;
            case "MCX" -> Exchange.MCX;
            case "CDS" -> Exchange.CDS;
            case "IDX", "INDEX" -> Exchange.INDEX;
            default -> Exchange.UNKNOWN;
        };
    }
    /**
     * Converts this definition to a domain Instrument.
     */

    public Instrument toInstrument() {
        String instrumentType = optionType != null && optionType != OptionType.UNKNOWN ? "OPT" : "EQ";
        Exchange exchange = mapExchange(exchangeName);
        return new Instrument(
                tradingSymbol,
                tradingSymbol,
                exchange,
                exchangeSegment,
                instrumentType,
                underlyingKey != null ? underlyingKey : tradingSymbol,
                expiry,
                strikePricePaisa > 0 ? strikePricePaisa : null,
                optionType,
                lotSize,
                tickSizePaisa
        );
    }

    /**
     * Creates an InstrumentKey for this definition.
     */
    public InstrumentKey toInstrumentKey() {
        return new InstrumentKey(tradingSymbol, exchangeSegment);
    }
}
