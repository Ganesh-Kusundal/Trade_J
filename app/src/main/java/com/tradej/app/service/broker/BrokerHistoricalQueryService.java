package com.tradej.app.service.broker;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;

import java.util.List;

/**
 * Fetches historical candles from the active broker {@link MarketDataProvider} (REST).
 * Windowing, pagination, and rate limiting are handled inside each broker provider.
 */
public final class BrokerHistoricalQueryService {

    private final MarketDataProvider marketDataProvider;

    public BrokerHistoricalQueryService(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    public long getLtpPaisa(InstrumentKey instrumentKey) {
        return marketDataProvider.getLtpPaisa(instrumentKey);
    }

    public List<Candle> getCandlesChunked(CandleHistoryRequest request) {
        return marketDataProvider.getCandles(request);
    }
}
