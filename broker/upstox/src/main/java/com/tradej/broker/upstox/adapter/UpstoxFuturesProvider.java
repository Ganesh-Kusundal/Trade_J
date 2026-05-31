package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class UpstoxFuturesProvider implements FuturesProvider {

    private final UpstoxInstrumentResolver instrumentResolver;

    public UpstoxFuturesProvider(UpstoxInstrumentResolver instrumentResolver) {
        this.instrumentResolver = instrumentResolver;
    }

    @Override
    public List<Instrument> getContracts(String underlying, ExchangeSegment segment) {
        return instrumentResolver.allInstruments().stream()
                .filter(i -> i.instrumentType() != null && i.instrumentType().startsWith("FUT"))
                .filter(i -> underlying.equalsIgnoreCase(i.underlying()))
                .filter(i -> i.exchangeSegment() == segment)
                .toList();
    }

    @Override
    public Instrument getNearestContract(String underlying, ExchangeSegment segment) {
        return getContracts(underlying, segment).stream()
                .filter(i -> i.expiry() != null)
                .min(Comparator.comparing(Instrument::expiry))
                .orElse(null);
    }

    @Override
    public List<LocalDate> getExpiries(String underlying, ExchangeSegment segment) {
        return getContracts(underlying, segment).stream()
                .map(Instrument::expiry)
                .filter(d -> d != null)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public boolean isCommodity(String underlying) {
        return false;
    }
}
