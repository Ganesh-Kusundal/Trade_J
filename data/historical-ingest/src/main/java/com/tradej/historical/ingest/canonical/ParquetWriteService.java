package com.tradej.historical.ingest.canonical;

import com.tradej.core.domain.model.Candle;

import java.time.LocalDate;
import java.util.List;

/**
 * Standard interface for writing candle data to canonical parquet storage.
 *
 * <p>All data write paths must go through this interface. There is exactly one
 * implementation ({@link CanonicalBarWriter}) that writes to the canonical
 * hive-partitioned layout:
 * <pre>
 *   data/historical/bars/segment=X/symbol=Y/interval=Z/year=YYYY/month=MM/part.parquet
 * </pre>
 *
 * <p>Consumers: {@code IncrementalSyncService}, {@code EquityDownloadJobService},
 * {@code BackfillService}, {@code HistoricalSyncScheduler}.
 */
public interface ParquetWriteService {

    /**
     * Write candles to canonical parquet storage.
     *
     * @param segment  exchange segment (e.g. "NSE_EQ")
     * @param symbol   trading symbol (e.g. "RELIANCE")
     * @param interval candle interval (e.g. "1m", "5m", "1d")
     * @param candles  the candle data to write
     * @return number of bars written
     */
    int writeBars(String segment, String symbol, String interval, List<Candle> candles);

    /**
     * Check if data exists for a symbol on a given date.
     */
    boolean hasData(String segment, String symbol, String interval, LocalDate date);
}
