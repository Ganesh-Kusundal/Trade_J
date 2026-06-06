package com.tradej.core.service;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Quote;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Unified market data facade shared by CLI, app, terminal, and MCP tools.
 * Framework-independent — no Spring.
 */
public interface MarketDataService {
    Quote getQuote(InstrumentKey key);
    long getLtpPaisa(InstrumentKey key);
    MarketDepth getDepth(InstrumentKey key);
    Quote getOhlcSnapshot(InstrumentKey key);
    List<Candle> getCandles(InstrumentKey key, String interval, LocalDate from, LocalDate to);
    Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> keys);
    Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> keys);
    OptionChainSnapshot getOptionChain(String underlying, com.tradej.core.domain.value.ExchangeSegment segment, LocalDate expiry);
}
