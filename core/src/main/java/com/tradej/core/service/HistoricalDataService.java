package com.tradej.core.service;

import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.Candle;

import java.util.List;

/**
 * Unified historical data facade shared by CLI, app, terminal, and MCP tools.
 * Framework-independent — no Spring.
 */
public interface HistoricalDataService {
    List<Candle> queryCandles(String symbol, String interval, long fromMs, long toMs, int limit);
    List<MarketTickEvent> queryTicks(String symbol, long fromMs, long toMs, int limit);
    long tickCount(String symbol, long fromMs, long toMs);
    long candleCount(String symbol, String interval, long fromMs, long toMs);
}
