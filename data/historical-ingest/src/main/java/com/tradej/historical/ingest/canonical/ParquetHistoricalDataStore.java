package com.tradej.historical.ingest.canonical;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.historical.ingest.calendar.TradingCalendarStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public final class ParquetHistoricalDataStore implements HistoricalDataStore {

    private static final Logger log = LoggerFactory.getLogger(ParquetHistoricalDataStore.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int DEFAULT_LIMIT = 50_000;
    private static final String DEFAULT_SEGMENT = "NSE_EQ";

    private final CanonicalBarQuery query;
    private final TradingCalendarStore calendar;
    private final Path barsRoot;

    public ParquetHistoricalDataStore(Path dataRoot, TradingCalendarStore calendar) {
        this.barsRoot = CanonicalPaths.barsRoot(dataRoot);
        this.query = new CanonicalBarQuery(barsRoot);
        this.calendar = calendar;
    }

    @Override
    public List<Candle> queryCandles(CandleHistoryRequest request) {
        return queryCandles(
                request.instrument().symbol(),
                request.instrument().exchangeSegment().name(),
                request.interval(),
                request.fromDate(),
                request.toDate());
    }

    @Override
    public List<Candle> queryCandles(String symbol, String segment, String interval,
                                      LocalDate from, LocalDate to) {
        try {
            long fromMs = from.atStartOfDay(IST).toInstant().toEpochMilli();
            long toMs = to.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;
            long daySpan = Math.max(1, to.toEpochDay() - from.toEpochDay() + 1);
            int limit = Math.max(DEFAULT_LIMIT, (int) daySpan * 500);
            List<Map<String, Object>> rows = query.queryBars(symbol, segment, interval, fromMs, toMs, limit);
            return mapRows(symbol, interval, rows);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query parquet for " + symbol + " " + interval, ex);
        }
    }

    @Override
    public boolean hasData(String symbol, String interval, LocalDate date) {
        try {
            long fromMs = date.atStartOfDay(IST).toInstant().toEpochMilli();
            long toMs = date.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;
            List<Map<String, Object>> rows = query.queryBars(symbol, DEFAULT_SEGMENT, interval, fromMs, toMs, 1);
            return !rows.isEmpty();
        } catch (SQLException ex) {
            return false;
        }
    }

    @Override
    public Set<LocalDate> availableDates(String symbol, String interval,
                                          LocalDate from, LocalDate to) {
        try {
            long fromMs = from.atStartOfDay(IST).toInstant().toEpochMilli();
            long toMs = to.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;
            List<Map<String, Object>> rows = query.queryBars(symbol, DEFAULT_SEGMENT, interval, fromMs, toMs, DEFAULT_LIMIT);
            Set<LocalDate> dates = new TreeSet<>();
            for (Map<String, Object> row : rows) {
                Object ts = row.get("bar_time_ms");
                if (ts instanceof Number num) {
                    dates.add(Instant.ofEpochMilli(num.longValue()).atZone(IST).toLocalDate());
                }
            }
            return dates;
        } catch (SQLException ex) {
            return Set.of();
        }
    }

    @Override
    public Optional<LocalDate> latestAvailableDate(String symbol, String interval) {
        LocalDate today = LocalDate.now(IST);
        Set<LocalDate> dates = availableDates(symbol, interval, today.minusDays(30), today);
        return dates.isEmpty() ? Optional.empty() : Optional.of(((TreeSet<LocalDate>) dates).last());
    }

    @Override
    public List<String> availableSymbols(String segment) {
        try {
            return query.availableSymbols(segment, "1m");
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<String> availableIntervals(String symbol, String segment) {
        return query.availableIntervals(symbol, segment);
    }

    public Map<String, Object> segmentSummary(String segment) {
        try {
            return query.summary(segment);
        } catch (SQLException ex) {
            return Map.of("error", ex.getMessage());
        }
    }

    @Override
    public DataQualityReport qualityCheck(String symbol, String interval,
                                           LocalDate from, LocalDate to) {
        Set<LocalDate> actualDates = availableDates(symbol, interval, from, to);
        var segment = com.tradej.core.domain.value.ExchangeSegment.NSE_EQ;
        List<LocalDate> tradingDays = calendar.tradingDays(segment, from, to);
        List<LocalDate> gapDates = new ArrayList<>();
        for (LocalDate day : tradingDays) {
            if (!actualDates.contains(day)) {
                gapDates.add(day);
            }
        }
        int expected = tradingDays.size();
        int actual = actualDates.size();
        double completeness = expected > 0 ? (actual * 100.0 / expected) : 100.0;
        return new DataQualityReport(symbol, interval, from, to,
                expected, actual, gapDates.size(), completeness, gapDates);
    }

    private List<Candle> mapRows(String symbol, String interval, List<Map<String, Object>> rows) {
        List<Candle> candles = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            long barTimeMs = longValue(row, "bar_time_ms");
            candles.add(new Candle(
                    symbol,
                    interval,
                    barTimeMs,
                    barTimeMs,
                    longValue(row, "open_paisa"),
                    longValue(row, "high_paisa"),
                    longValue(row, "low_paisa"),
                    longValue(row, "close_paisa"),
                    longValue(row, "volume"),
                    true,
                    longValue(row, "oi"),
                    longValue(row, "trades")
            ));
        }
        return candles;
    }

    private static long longValue(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof Number num) return num.longValue();
        return 0L;
    }
}
