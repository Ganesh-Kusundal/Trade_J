package com.tradej.broker.dhan.instrument;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.InstrumentResolverContractTest;
import org.junit.jupiter.api.Tag;

import java.util.List;

/**
 * Contract test for {@link DhanInstrumentCatalog}.
 *
 * <p>Loads test data consisting of a single SBIN equity instrument
 * with security ID 3045 on NSE_EQ.
 */
@Tag("unit")
final class DhanInstrumentCatalogContractTest extends InstrumentResolverContractTest {

    @Override
    protected InstrumentResolver createResolver() {
        return new DhanInstrumentCatalog();
    }

    @Override
    protected void populateResolver(InstrumentResolver resolver) {
        DhanInstrumentCatalog catalog = (DhanInstrumentCatalog) resolver;
        DhanInstrumentDefinition sbin = new DhanInstrumentDefinition(
                "SBIN",
                "SBIN",
                com.tradej.core.domain.value.Exchange.NSE,
                com.tradej.core.domain.value.ExchangeSegment.NSE_EQ,
                "3045",
                "EQUITY",
                "SBIN",
                null,
                null,
                com.tradej.core.domain.value.OptionType.UNKNOWN,
                1L,
                1_000L,
                null
        );
        catalog.replaceAll(List.of(sbin));
    }
}
