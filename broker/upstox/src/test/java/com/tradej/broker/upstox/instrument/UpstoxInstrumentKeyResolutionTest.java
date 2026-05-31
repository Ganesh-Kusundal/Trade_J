package com.tradej.broker.upstox.instrument;

import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class UpstoxInstrumentKeyResolutionTest {

    @Test
    void niftyResolvesToIndexInstrumentKeyWhenCatalogLoaded() {
        UpstoxInstrumentResolver resolver = new UpstoxInstrumentResolver();
        resolver.register(new UpstoxInstrumentDefinition(
                0L,
                "NSE_INDEX|Nifty 50",
                "NIFTY",
                "NSE",
                ExchangeSegment.IDX_I,
                "Nifty 50",
                null,
                1L,
                5L,
                OptionType.UNKNOWN,
                0L,
                null,
                "NSE_INDEX|Nifty 50"
        ));

        InstrumentKey key = new InstrumentKey(
                ContractSymbolNormalizer.normalize("NIFTY"),
                ExchangeSegment.IDX_I
        );
        assertEquals("NSE_INDEX|Nifty 50", resolver.requireInstrumentKey(key));
    }
}
