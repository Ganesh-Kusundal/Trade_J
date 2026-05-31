package com.tradej.broker.icici.instrument;

import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class BreezeInstrumentDefinitionTest {

    @Test
    void exposesTradingSymbolAsCanonical() {
        BreezeInstrumentDefinition definition = new BreezeInstrumentDefinition(
                "RELIND",
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                "2885",
                "4.1!2885",
                "NSE"
        );
        assertEquals("RELIND", definition.breezeStockCode());
        assertEquals("RELIANCE", definition.tradingSymbol());
        assertEquals("RELIANCE", definition.toInstrument().canonicalSymbol());
        assertEquals("RELIANCE", definition.toInstrumentKey().symbol());
    }
}
