package com.tradej.broker.core.service;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.service.MarketDataService;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared implementation of {@link MarketDataService}.
 * Works with any broker adapter — no Spring required.
 */
public final class MarketDataServiceImpl implements MarketDataService {

    private final MarketDataProvider marketData;
    private final OptionsProvider options;

    public MarketDataServiceImpl(MarketDataProvider marketData, OptionsProvider options) {
        this.marketData = marketData;
        this.options = options;
    }

    @Override
    public Quote getQuote(InstrumentKey key) { return marketData.getQuote(key); }

    @Override
    public long getLtpPaisa(InstrumentKey key) { return marketData.getLtpPaisa(key); }

    @Override
    public MarketDepth getDepth(InstrumentKey key) { return marketData.getDepth(key); }

    @Override
    public Quote getOhlcSnapshot(InstrumentKey key) { return marketData.getOhlcSnapshot(key); }

    @Override
    public List<Candle> getCandles(InstrumentKey key, String interval, LocalDate from, LocalDate to) {
        return marketData.getCandles(new CandleHistoryRequest(key, interval, from, to));
    }

    @Override
    public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> keys) {
        return marketData.getLtpBatch(keys);
    }

    @Override
    public Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> keys) {
        return marketData.getQuoteBatch(keys);
    }

    @Override
    public OptionChainSnapshot getOptionChain(String underlying, ExchangeSegment segment, LocalDate expiry) {
        return options.getOptionChain(underlying, segment, expiry);
    }
}
