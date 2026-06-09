package com.tradej.historical.ingest.sync;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.calendar.TradingCalendarStore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Detects gaps in historical candle data by comparing expected bar timestamps
 * (from trading calendar × market hours) against actual data.
 *
 * <p>Used by the incremental sync and backfill services to identify
 * missing candles that need to be re-downloaded.
 */
public final class GapDetector {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private final TradingCalendarStore calendar;

    public GapDetector(TradingCalendarStore calendar) {
        this.calendar = calendar;
    }

    /**
     * Generate all expected 1-minute bar timestamps for a symbol and date range.
     * Accounts for trading days only and market hours (09:15 - 15:30 IST).
     */
    public Set<Long> expectedBarTimestamps1m(ExchangeSegment segment,
                                              LocalDate from, LocalDate to) {
        Set<Long> timestamps = new LinkedHashSet<>();
        for (LocalDate date : calendar.tradingDays(segment, from, to)) {
            ZonedDateTime open = date.atTime(MARKET_OPEN).atZone(IST);
            ZonedDateTime close = date.atTime(MARKET_CLOSE).atZone(IST);
            ZonedDateTime cursor = open;
            while (cursor.isBefore(close)) {
                timestamps.add(cursor.toInstant().toEpochMilli());
                cursor = cursor.plusMinutes(1);
            }
        }
        return timestamps;
    }

    /**
     * Generate expected bar timestamps for any interval.
     */
    public Set<Long> expectedBarTimestamps(ExchangeSegment segment,
                                            String interval,
                                            LocalDate from, LocalDate to) {
        int intervalMinutes = parseIntervalMinutes(interval);
        if (intervalMinutes == 1) {
            return expectedBarTimestamps1m(segment, from, to);
        }
        Set<Long> timestamps = new LinkedHashSet<>();
        for (LocalDate date : calendar.tradingDays(segment, from, to)) {
            ZonedDateTime open = date.atTime(MARKET_OPEN).atZone(IST);
            ZonedDateTime close = date.atTime(MARKET_CLOSE).atZone(IST);
            ZonedDateTime cursor = open;
            while (cursor.plusMinutes(intervalMinutes).isBefore(close) || cursor.plusMinutes(intervalMinutes).equals(close)) {
                timestamps.add(cursor.toInstant().toEpochMilli());
                cursor = cursor.plusMinutes(intervalMinutes);
            }
        }
        return timestamps;
    }

    /**
     * Detect missing bar timestamps by subtracting actual from expected.
     */
    public Set<Long> detectGaps(ExchangeSegment segment, String interval,
                                 LocalDate from, LocalDate to,
                                 Set<Long> actualTimestamps) {
        Set<Long> expected = expectedBarTimestamps(segment, interval, from, to);
        expected.removeAll(actualTimestamps);
        return expected;
    }

    /**
     * Expected number of 1m bars per trading day (375 = 6h15m).
     */
    public static int barsPerTradingDay1m() {
        return 375;
    }

    private static int parseIntervalMinutes(String interval) {
        if (interval == null) return 1;
        return switch (interval.trim().toLowerCase()) {
            case "1m" -> 1;
            case "5m" -> 5;
            case "15m" -> 15;
            case "25m" -> 25;
            case "30m" -> 30;
            case "60m", "1h" -> 60;
            default -> 1;
        };
    }
}
