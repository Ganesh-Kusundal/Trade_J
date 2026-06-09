package com.tradej.historical.ingest.canonical;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Unified data access interface for all historical data.
 *
 * <p>Consumers never know whether data comes from parquet files, DuckDB,
 * a cache, or a live broker API. Implementations handle routing internally.
 *
 * <p>Supersedes {@link com.tradej.core.domain.port.HistoricalBarRepository}
 * and {@link com.tradej.core.domain.port.HistoricalAnalyticsService} with a
 * single, clean contract.
 */
public interface HistoricalDataStore {

    List<Candle> queryCandles(CandleHistoryRequest request);

    List<Candle> queryCandles(String symbol, String segment, String interval,
                               LocalDate from, LocalDate to);

    boolean hasData(String symbol, String interval, LocalDate date);

    Set<LocalDate> availableDates(String symbol, String interval,
                                   LocalDate from, LocalDate to);

    Optional<LocalDate> latestAvailableDate(String symbol, String interval);

    List<String> availableSymbols(String segment);

    DataQualityReport qualityCheck(String symbol, String interval,
                                    LocalDate from, LocalDate to);

    record DataQualityReport(
            String symbol,
            String interval,
            LocalDate from,
            LocalDate to,
            int expectedBars,
            int actualBars,
            int missingBars,
            double completenessPercent,
            List<LocalDate> gapDates
    ) {
        public boolean isComplete() {
            return missingBars == 0;
        }
    }
}
