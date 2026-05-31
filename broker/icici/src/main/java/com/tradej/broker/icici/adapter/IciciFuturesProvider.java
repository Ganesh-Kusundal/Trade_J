package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.icici.instrument.BreezeInstrumentDefinition;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class IciciFuturesProvider implements FuturesProvider {
    private final BreezeInstrumentResolver instrumentResolver;

    public IciciFuturesProvider(BreezeInstrumentResolver instrumentResolver) {
        this.instrumentResolver = instrumentResolver;
    }

    @Override
    public List<Instrument> getContracts(String underlying, ExchangeSegment exchangeSegment) {
        try {
            BreezeInstrumentDefinition definition = instrumentResolver.requireBreezeDefinition(
                    new InstrumentKey(underlying, exchangeSegment));
            return List.of(definition.toInstrument());
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    @Override
    public Instrument getNearestContract(String underlying, ExchangeSegment exchangeSegment) {
        List<Instrument> contracts = getContracts(underlying, exchangeSegment);
        if (contracts.isEmpty()) {
            throw new IllegalArgumentException("No ICICI futures contract for " + underlying);
        }
        return contracts.getFirst();
    }

    @Override
    public List<LocalDate> getExpiries(String underlying, ExchangeSegment exchangeSegment) {
        return new ArrayList<>();
    }

    @Override
    public boolean isCommodity(String underlying) {
        return false;
    }
}
