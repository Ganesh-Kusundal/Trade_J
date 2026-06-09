package com.tradej.historical.ingest.query;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.UniverseEntry;
import com.tradej.core.domain.port.HistoricalBarRepository;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parquet-backed implementation of {@link HistoricalBarRepository}.
 * Delegates to {@link EquityHistoricalQuery} for DuckDB-based parquet reads.
 */
public final class ParquetHistoricalBarRepository implements HistoricalBarRepository, AutoCloseable {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int DEFAULT_LIMIT = 50_000;

    private final EquityHistoricalQuery delegate;

    public ParquetHistoricalBarRepository(Path rootPath) {
        this.delegate = new EquityHistoricalQuery(rootPath);
    }

    @Override
    public List<Candle> queryCandles(CandleHistoryRequest request) {
        return queryCandles(request.instrument(), request.interval(), request.fromDate(), request.toDate());
    }

    @Override
    public List<Candle> queryCandles(InstrumentKey instrument, String interval, LocalDate from, LocalDate to) {
        try {
            long fromMs = from.atStartOfDay(IST).toInstant().toEpochMilli();
            long toMs = to.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli();
            List<Map<String, Object>> rows = delegate.queryCandles(instrument.symbol(), fromMs, toMs, DEFAULT_LIMIT);
            return rows.stream().map(ParquetHistoricalBarRepository::toCandle).toList();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query candles for " + instrument.symbol(), e);
        }
    }

    @Override
    public List<Candle> queryIntradayBars(List<String> symbols, LocalDate date) {
        try {
            long fromMs = date.atStartOfDay(IST).toInstant().toEpochMilli();
            long toMs = date.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli();
            return delegate.queryCandlesForSymbols(symbols, fromMs, toMs, DEFAULT_LIMIT)
                    .stream()
                    .map(ParquetHistoricalBarRepository::toCandle)
                    .toList();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query intraday bars", e);
        }
    }

    @Override
    public List<Candle> queryBenchmarkBars(LocalDate date, String benchmarkSymbol) {
        return queryCandles(
                InstrumentKey.of(benchmarkSymbol, com.tradej.core.domain.value.ExchangeSegment.IDX_I),
                "1m", date, date);
    }

    @Override
    public List<UniverseEntry> queryUniverse() {
        try {
            return delegate.queryUniverse().stream()
                    .map(row -> new UniverseEntry(
                            str(row, "symbol"),
                            str(row, "company_name"),
                            str(row, "isin"),
                            str(row, "industry"),
                            str(row, "macro_sector"),
                            null
                    ))
                    .toList();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query universe", e);
        }
    }

    @Override
    public List<String> querySymbols(int limit) {
        return queryUniverse().stream()
                .map(UniverseEntry::symbol)
                .limit(limit)
                .toList();
    }

    @Override
    public List<String> querySymbolsWithDataOn(LocalDate date, int limit) {
        try {
            return delegate.querySymbolsWithDataOn(date, limit);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query symbols with data on " + date, e);
        }
    }

    @Override
    public Optional<LocalDate> latestAvailableTradingDay(int lookbackDays) {
        try {
            return delegate.latestAvailableTradingDay(lookbackDays);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query latest trading day", e);
        }
    }

    private static Candle toCandle(Map<String, Object> row) {
        long startTime = getLongValue(row, "start_time_ms", "startTimeMs", "bar_time_ms", "barTimeMs");
        long endTime = getLongValue(row, "end_time_ms", "endTimeMs");
        if (endTime == 0L && startTime > 0L) {
            String interval = str(row, "interval", "1m");
            long durationMs = parseIntervalToMs(interval);
            endTime = startTime + durationMs;
        }
        return new Candle(
                str(row, "symbol"),
                str(row, "interval", "1m"),
                startTime,
                endTime,
                getLongValue(row, "open_paisa", "openPaisa"),
                getLongValue(row, "high_paisa", "highPaisa"),
                getLongValue(row, "low_paisa", "lowPaisa"),
                getLongValue(row, "close_paisa", "closePaisa"),
                getLongValue(row, "volume"),
                true
        );
    }

    private static long getLongValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object val = row.get(key);
            if (val != null) {
                return toLong(val);
            }
        }
        return 0L;
    }

    private static long parseIntervalToMs(String interval) {
        if (interval == null || interval.isBlank()) {
            return 60_000L;
        }
        try {
            String clean = interval.toLowerCase().trim();
            if (clean.endsWith("m") || clean.endsWith("minute") || clean.endsWith("min")) {
                int val = Integer.parseInt(clean.replaceAll("[a-z]", ""));
                return val * 60_000L;
            }
            if (clean.endsWith("h") || clean.endsWith("hour")) {
                int val = Integer.parseInt(clean.replaceAll("[a-z]", ""));
                return val * 3600_000L;
            }
            if (clean.endsWith("d") || clean.endsWith("day")) {
                int val = Integer.parseInt(clean.replaceAll("[a-z]", ""));
                return val * 86400_000L;
            }
        } catch (Exception ignored) {
        }
        return 60_000L;
    }

    private static String str(Map<String, Object> row, String key) {
        return str(row, key, "");
    }

    private static String str(Map<String, Object> row, String key, String defaultValue) {
        Object value = row.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public void close() {
        delegate.close();
    }
}
