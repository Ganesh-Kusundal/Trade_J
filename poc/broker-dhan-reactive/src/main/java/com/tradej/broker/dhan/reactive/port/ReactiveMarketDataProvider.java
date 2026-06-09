package com.tradej.broker.dhan.reactive.port;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;

/**
 * Reactive market data provider interface.
 */
public interface ReactiveMarketDataProvider {
    
    /**
     * Get last traded price in paise (smallest currency unit).
     */
    Mono<Long> getLtpPaisa(InstrumentKey instrumentKey);
    
    /**
     * Get full quote snapshot (OHLC, volume, etc.).
     */
    Mono<Quote> getQuote(InstrumentKey instrumentKey);
    
    /**
     * Get market depth (order book).
     */
    Mono<MarketDepth> getDepth(InstrumentKey instrumentKey);
    
    /**
     * Get historical candle data.
     */
    Flux<Candle> getCandles(CandleHistoryRequest request);
    
    /**
     * Get LTP for multiple instruments in batch.
     */
    Mono<Map<InstrumentKey, Long>> getLtpBatch(Collection<InstrumentKey> instrumentKeys);
    
    /**
     * Get quotes for multiple instruments in batch.
     */
    Mono<Map<InstrumentKey, Quote>> getQuoteBatch(Collection<InstrumentKey> instrumentKeys);
}
