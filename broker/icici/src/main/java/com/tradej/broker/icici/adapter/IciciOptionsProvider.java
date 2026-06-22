package com.tradej.broker.icici.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.icici.instrument.BreezeInstrumentDefinition;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.rest.BreezeOptionChainRestClient;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.RollingOptionSeries;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class IciciOptionsProvider implements OptionsProvider {
    private final BreezeOptionChainRestClient restClient;
    private final BreezeInstrumentResolver instrumentResolver;
    private final BreezeDomainMapper mapper;

    public IciciOptionsProvider(
            BreezeOptionChainRestClient restClient,
            BreezeInstrumentResolver instrumentResolver,
            BreezeDomainMapper mapper
    ) {
        this.restClient = restClient;
        this.instrumentResolver = instrumentResolver;
        this.mapper = mapper;
    }

    @Override
    public List<LocalDate> getExpiries(String underlying, ExchangeSegment exchangeSegment) {
        return List.of(LocalDate.now().plusDays(7));
    }

    @Override
    public List<Instrument> getOptionContracts(String underlying, ExchangeSegment exchangeSegment, LocalDate expiry) {
        return getOptionChain(underlying, exchangeSegment, expiry).strikes().stream()
                .flatMap(entry -> {
                    List<Instrument> instruments = new ArrayList<>(2);
                    if (entry.call() != null) {
                        instruments.add(entry.call().instrument());
                    }
                    if (entry.put() != null) {
                        instruments.add(entry.put().instrument());
                    }
                    return instruments.stream();
                })
                .toList();
    }

    @Override
    public OptionChainSnapshot getOptionChain(String underlying, ExchangeSegment exchangeSegment, LocalDate expiry) {
        BreezeInstrumentDefinition definition = instrumentResolver.requireBreezeDefinition(
                InstrumentKey.of(underlying, exchangeSegment));
        JsonNode chain = restClient.getOptionChain(mapper.toOptionChainPayload(definition, expiry));
        Instrument underlyingInst = definition.toInstrument();
        List<OptionChainEntry> strikes = new ArrayList<>();
        if (chain != null && chain.isArray()) {
            for (JsonNode node : chain) {
                long strikePaisa = Math.round(node.path("strike_price").asDouble(0.0) * 100.0);
                OptionQuote call = buildQuote(node, underlying, exchangeSegment, expiry, strikePaisa, OptionType.CALL);
                OptionQuote put = buildQuote(node, underlying, exchangeSegment, expiry, strikePaisa, OptionType.PUT);
                strikes.add(new OptionChainEntry(strikePaisa, call, put));
            }
        }
        return new OptionChainSnapshot(underlyingInst, expiry, 0L, strikes);
    }

    @Override
    public OptionQuote getGreeks(InstrumentKey instrumentKey) {
        throw new UnsupportedOperationException("ICICI Breeze greeks not available");
    }

    @Override
    public RollingOptionSeries getExpiredOptionHistory(RollingOptionHistoryRequest request) {
        throw new UnsupportedOperationException("ICICI expired option history not available");
    }

    @Override
    public long selectStrikePaisa(
            String underlying,
            ExchangeSegment exchangeSegment,
            long spotPricePaisa,
            OptionType optionType,
            StrikeSelectionKind selectionKind,
            int depth
    ) {
        List<LocalDate> expiries = getExpiries(underlying, exchangeSegment);
        if (expiries.isEmpty()) {
            return spotPricePaisa;
        }
        return getOptionChain(underlying, exchangeSegment, expiries.getFirst()).strikes().stream()
                .mapToLong(OptionChainEntry::strikePricePaisa)
                .min()
                .orElse(spotPricePaisa);
    }

    private OptionQuote buildQuote(
            JsonNode node,
            String underlying,
            ExchangeSegment exchangeSegment,
            LocalDate expiry,
            long strikePaisa,
            OptionType optionType
    ) {
        Instrument instrument = new Instrument(
                underlying,
                underlying,
                com.tradej.core.domain.value.Exchange.NFO,
                exchangeSegment,
                "OPT",
                underlying,
                expiry,
                strikePaisa,
                optionType,
                1L,
                1L
        );
        return new OptionQuote(
                instrument,
                Math.round(node.path("ltp").asDouble(0.0) * 100.0),
                node.path("open_interest").asLong(0L),
                node.path("volume").asLong(0L),
                0L,
                0L,
                0L,
                0L,
                new OptionGreeks(null, null, null, null, null)
        );
    }
}
