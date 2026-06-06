package com.tradej.historical.service;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;

import java.util.List;

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
