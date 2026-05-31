package com.tradej.analytics.repository;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.UniverseEntry;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.historical.ingest.resample.CandleResampler;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FederatedHistoricalBarRepository implements HistoricalBarRepository {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int DEFAULT_LIMIT = 50_000;
    private static final List<String> BENCHMARK_SYMBOLS = List.of("NIFTY", "BANKNIFTY", "NIFTY 50");

    private final DuckDbAnalyticsEngine engine;

    public FederatedHistoricalBarRepository(DuckDbAnalyticsEngine engine) {
        this.engine = engine;
    }

    @Override
    public List<Candle> queryCandles(CandleHistoryRequest request) {
        return queryCandles(request.instrument(), request.interval(), request.fromDate(), request.toDate());
    }

    @Override
    public List<Candle> queryCandles(InstrumentKey instrument, String interval, LocalDate from, LocalDate to) {
        try {
            long fromMs = from.atStartOfDay(IST).toInstant().toEpochMilli();
            long toMs = to.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;
            long daySpan = Math.max(1, to.toEpochDay() - from.toEpochDay() + 1);
            int limit = Math.max(DEFAULT_LIMIT, (int) daySpan * 500);
            List<Map<String, Object>> rows = engine.queryEquityCandles(instrument.symbol(), fromMs, toMs, limit);
            List<Candle> oneMinute = mapRows(instrument.symbol(), "1m", rows);
            return CandleResampler.resample(oneMinute, interval);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query federated equity candles for " + instrument.symbol(), ex);
        }
    }

    @Override
    public List<Candle> queryIntradayBars(List<String> symbols, LocalDate date) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        long fromMs = date.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = date.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;
        int rowLimit = Math.max(DEFAULT_LIMIT, symbols.size() * 400);
        try {
            List<Map<String, Object>> rows = engine.queryEquityCandlesForSymbols(symbols, fromMs, toMs, rowLimit);
            Map<String, List<Map<String, Object>>> bySymbol = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                bySymbol.computeIfAbsent(stringValue(row.get("symbol")), ignored -> new ArrayList<>()).add(row);
            }
            List<Candle> all = new ArrayList<>(rows.size());
            for (String symbol : symbols) {
                all.addAll(mapRows(symbol, "1m", bySymbol.getOrDefault(symbol, List.of())));
            }
            return all;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query intraday bars for " + date, ex);
        }
    }

    @Override
    public List<Candle> queryBenchmarkBars(LocalDate date, String benchmarkSymbol) {
        for (String candidate : benchmarkSymbol == null || benchmarkSymbol.isBlank()
                ? BENCHMARK_SYMBOLS
                : List.of(benchmarkSymbol)) {
            List<Candle> bars = queryIntradayBars(List.of(candidate), date);
            if (!bars.isEmpty()) {
                return bars;
            }
        }
        return List.of();
    }

    @Override
    public List<UniverseEntry> queryUniverse() {
        try {
            List<Map<String, Object>> rows = engine.queryEquityUniverse();
            List<UniverseEntry> entries = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                LocalDate asOfDate = null;
                Object rawDate = row.get("asOfDate");
                if (rawDate instanceof java.sql.Date sqlDate) {
                    asOfDate = sqlDate.toLocalDate();
                } else if (rawDate instanceof LocalDate localDate) {
                    asOfDate = localDate;
                }
                entries.add(new UniverseEntry(
                        stringValue(row.get("symbol")),
                        stringValue(row.get("companyName")),
                        stringValue(row.get("isin")),
                        stringValue(row.get("industry")),
                        stringValue(row.get("macroSector")),
                        asOfDate
                ));
            }
            return entries;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query equity universe", ex);
        }
    }

    @Override
    public List<String> querySymbols(int limit) {
        return queryUniverse().stream()
                .map(UniverseEntry::symbol)
                .filter(s -> s != null && !s.isBlank())
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public List<String> querySymbolsWithDataOn(LocalDate date, int limit) {
        try {
            return engine.queryEquitySymbolsWithDataOn(date, limit);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query symbols with data on " + date, ex);
        }
    }

    @Override
    public Optional<LocalDate> latestAvailableTradingDay(int lookbackDays) {
        try {
            return engine.latestEquityTradingDay(lookbackDays);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to resolve latest trading day", ex);
        }
    }

    private static List<Candle> mapRows(String symbol, String interval, List<Map<String, Object>> rows) {
        List<Candle> candles = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            long barTimeMs = longValue(row.get("barTimeMs"));
            candles.add(new Candle(
                    symbol,
                    interval,
                    barTimeMs,
                    barTimeMs + 60_000L - 1,
                    longValue(row.get("openPaisa")),
                    longValue(row.get("highPaisa")),
                    longValue(row.get("lowPaisa")),
                    longValue(row.get("closePaisa")),
                    longValue(row.get("volume")),
                    true
            ));
        }
        return candles;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
