package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MarketDataProvider {
    long getLtpPaisa(InstrumentKey instrumentKey);

    Quote getQuote(InstrumentKey instrumentKey);

    MarketDepth getDepth(InstrumentKey instrumentKey);

    Quote getOhlcSnapshot(InstrumentKey instrumentKey);

    List<Candle> getCandles(CandleHistoryRequest request);

    Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> instrumentKeys);

    Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> instrumentKeys);

    Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> instrumentKeys);
}
