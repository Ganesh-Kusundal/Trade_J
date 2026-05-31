package com.tradej.core.domain.port;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.UniverseEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Port for reading hive-partitioned equity historical bars from the canonical parquet warehouse.
 * <p>
 * Contract summary (see {@code docs/contracts/HISTORICAL_BAR_CONTRACT.md}):
 * <ul>
 *   <li>{@link #queryCandles} — resampled to the requested interval (1m source, IST session buckets)</li>
 *   <li>{@link #queryIntradayBars} — raw 1m bars for institutional scan input</li>
 *   <li>{@link #queryBenchmarkBars} — raw 1m benchmark session bars</li>
 *   <li>{@link #latestAvailableTradingDay(int)} — latest IST date with bar data; {@code lookbackDays=0} disables age filter</li>
 * </ul>
 */
public interface HistoricalBarRepository {

    List<Candle> queryCandles(CandleHistoryRequest request);

    List<Candle> queryCandles(InstrumentKey instrument, String interval, LocalDate from, LocalDate to);

    List<Candle> queryIntradayBars(List<String> symbols, LocalDate date);

    List<Candle> queryBenchmarkBars(LocalDate date, String benchmarkSymbol);

    List<UniverseEntry> queryUniverse();

    List<String> querySymbols(int limit);

    List<String> querySymbolsWithDataOn(LocalDate date, int limit);

    Optional<LocalDate> latestAvailableTradingDay(int lookbackDays);
}
