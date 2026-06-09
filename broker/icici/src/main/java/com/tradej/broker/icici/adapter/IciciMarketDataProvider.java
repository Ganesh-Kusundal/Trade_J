package com.tradej.broker.icici.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.icici.historical.BreezeHistoricalDataService;
import com.tradej.broker.icici.instrument.BreezeInstrumentDefinition;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.rest.BreezeMarketDataRestClient;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IciciMarketDataProvider implements MarketDataProvider {

    private final BreezeMarketDataRestClient marketDataRestClient;
    private final BreezeHistoricalDataService historicalDataService;
    private final BreezeInstrumentResolver instrumentResolver;
    private final BreezeDomainMapper mapper;

    public IciciMarketDataProvider(
            BreezeMarketDataRestClient marketDataRestClient,
            BreezeHistoricalDataService historicalDataService,
            BreezeInstrumentResolver instrumentResolver,
            BreezeDomainMapper mapper
    ) {
        this.marketDataRestClient = marketDataRestClient;
        this.historicalDataService = historicalDataService;
        this.instrumentResolver = instrumentResolver;
        this.mapper = mapper;
    }

    @Override
    public long getLtpPaisa(InstrumentKey instrumentKey) {
        return getQuote(instrumentKey).ltpPaisa();
    }

    @Override
    public Quote getQuote(InstrumentKey instrumentKey) {
        BreezeInstrumentDefinition definition = instrumentResolver.requireBreezeDefinition(instrumentKey);
        JsonNode node = marketDataRestClient.getQuotes(mapper.toQuotesPayload(definition));
        return mapper.toQuote(node, definition.toInstrument());
    }

    @Override
    public MarketDepth getDepth(InstrumentKey instrumentKey) {
        BreezeInstrumentDefinition definition = instrumentResolver.requireBreezeDefinition(instrumentKey);
        JsonNode node = marketDataRestClient.getDepth(mapper.toQuotesPayload(definition));
        return mapper.toDepth(node, definition.toInstrument());
    }

    @Override
    public Quote getOhlcSnapshot(InstrumentKey instrumentKey) {
        return getQuote(instrumentKey);
    }

    @Override
    public List<Candle> getCandles(CandleHistoryRequest request) {
        validateInterval(request.interval());
        BreezeInstrumentDefinition definition = instrumentResolver.requireBreezeDefinition(request.instrument());
        return historicalDataService.fetchCandles(request, definition);
    }

    @Override
    public com.tradej.broker.api.model.HistoricalDataCapabilities capabilities() {
        return com.tradej.broker.api.model.HistoricalDataCapabilities.iciciDefaults();
    }

    @Override
    public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> instrumentKeys) {
        Map<InstrumentKey, Long> result = new HashMap<>();
        for (InstrumentKey key : instrumentKeys) {
            result.put(key, getLtpPaisa(key));
        }
        return result;
    }

    @Override
    public Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> instrumentKeys) {
        Map<InstrumentKey, Quote> result = new HashMap<>();
        for (InstrumentKey key : instrumentKeys) {
            result.put(key, getQuote(key));
        }
        return result;
    }

    @Override
    public Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> instrumentKeys) {
        return getQuoteBatch(instrumentKeys);
    }
}
