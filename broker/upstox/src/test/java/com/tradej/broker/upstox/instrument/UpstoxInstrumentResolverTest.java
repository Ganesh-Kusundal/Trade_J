package com.tradej.broker.upstox.instrument;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class UpstoxInstrumentResolverTest {

    @Test
    void resolvesInstrumentKeyFromCatalogRegistration() {
        UpstoxInstrumentResolver resolver = new UpstoxInstrumentResolver();
        resolver.register(new UpstoxInstrumentDefinition(
                11536L,
                "NSE_EQ|INE002A01018",
                "SBIN",
                "NSE",
                ExchangeSegment.NSE_EQ,
                "STATE BANK OF INDIA",
                "INE002A01018",
                1L,
                5L,
                com.tradej.core.domain.value.OptionType.UNKNOWN,
                0L,
                null,
                "NSE_EQ|INE002A01018"
        ));

        assertEquals("NSE_EQ|INE002A01018",
                resolver.requireInstrumentKey(new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ)));
        assertEquals("SBIN", resolver.resolve(new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ)).symbol());
    }

    @Test
    void failsWhenCatalogMissingInstrument() {
        UpstoxInstrumentResolver resolver = new UpstoxInstrumentResolver();
        assertThrows(IllegalArgumentException.class, () ->
                resolver.requireInstrumentKey(new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ)));
    }
}
