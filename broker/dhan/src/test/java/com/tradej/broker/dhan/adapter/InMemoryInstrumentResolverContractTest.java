package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.InstrumentResolverContractTest;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import org.junit.jupiter.api.Tag;

import java.util.List;

/**
 * Contract test for {@link InMemoryInstrumentResolver}.
 *
 * <p>Loads test data consisting of a single SBIN equity instrument
 * with security ID 3045 on NSE_EQ.
 */
@Tag("unit")
final class InMemoryInstrumentResolverContractTest extends InstrumentResolverContractTest {

    @Override
    protected InstrumentResolver createResolver() {
        return new InMemoryInstrumentResolver();
    }

    @Override
    protected void populateResolver(InstrumentResolver resolver) {
        InMemoryInstrumentResolver r = (InMemoryInstrumentResolver) resolver;
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
        r.replaceDefinitions(List.of(sbin));
    }
}
