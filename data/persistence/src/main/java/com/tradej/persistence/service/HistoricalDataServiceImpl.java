package com.tradej.persistence.service;

import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.service.HistoricalDataService;
import com.tradej.persistence.replay.HistoricalRangeService;

import java.nio.file.Path;
import java.util.List;

/**
 * Shared implementation of {@link HistoricalDataService} backed by DuckDB.
 * Uses the same {@link HistoricalRangeService} as the trade-app runtime.
 */
public final class HistoricalDataServiceImpl implements HistoricalDataService {

    private final HistoricalRangeService delegate;

    public HistoricalDataServiceImpl(Path databasePath) {
        this.delegate = new HistoricalRangeService(databasePath);
    }

    public HistoricalDataServiceImpl(HistoricalRangeService delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Candle> queryCandles(String symbol, String interval, long fromMs, long toMs, int limit) {
        return delegate.queryCandles(symbol, interval, fromMs, toMs, limit);
    }

    @Override
    public List<MarketTickEvent> queryTicks(String symbol, long fromMs, long toMs, int limit) {
        return delegate.queryTicks(symbol, fromMs, toMs, limit);
    }

    @Override
    public long tickCount(String symbol, long fromMs, long toMs) {
        var stats = delegate.rangeStats(symbol, fromMs, toMs);
        return stats.tickCount();
    }

    @Override
    public long candleCount(String symbol, String interval, long fromMs, long toMs) {
        var stats = delegate.rangeStats(symbol, fromMs, toMs);
        return stats.candleCount();
    }
}
