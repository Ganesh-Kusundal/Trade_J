package com.tradej.app.service.broker;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class HistoricalRangeService {
    private static final int CHUNK_DAYS = 90;

    private final MarketDataProvider marketDataProvider;

    public HistoricalRangeService(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    public List<Candle> getCandlesChunked(CandleHistoryRequest request) {
        LocalDate start = request.fromDate();
        LocalDate end = request.toDate();
        if (start == null || end == null || start.isAfter(end)) {
            throw new IllegalArgumentException("Invalid historical range");
        }
        List<Candle> out = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            LocalDate chunkEnd = cursor.plusDays(CHUNK_DAYS - 1L);
            if (chunkEnd.isAfter(end)) {
                chunkEnd = end;
            }
            CandleHistoryRequest chunk = new CandleHistoryRequest(
                    request.instrument(),
                    request.interval(),
                    cursor,
                    chunkEnd
            );
            out.addAll(marketDataProvider.getCandles(chunk));
            cursor = chunkEnd.plusDays(1L);
        }
        return List.copyOf(out);
    }
}
