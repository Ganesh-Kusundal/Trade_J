package com.tradej.core.domain.time;

import com.tradej.core.domain.value.ExchangeSegment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Canonical market-local time contract for live, replay, and backtest flows.
 */
public final class MarketTime {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private MarketTime() {
    }

    public static LocalDate tradingDate(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(IST).toLocalDate();
    }

    public static DayBounds dayBoundsInclusive(LocalDate date) {
        long startMs = startOfDayMs(date);
        return new DayBounds(startMs, startOfDayMs(date.plusDays(1)) - 1L);
    }

    public static DayBounds dayBoundsExclusive(LocalDate date) {
        return new DayBounds(startOfDayMs(date), startOfDayMs(date.plusDays(1)));
    }

    public static SessionBounds sessionBounds(ExchangeSegment segment, LocalDate date) {
        ExchangeCalendar.SessionHours hours = ExchangeCalendar.sessionHours(segment);
        return new SessionBounds(
                atTimeMs(date, hours.preOpen()),
                atTimeMs(date, hours.open()),
                atTimeMs(date, hours.close())
        );
    }

    public static long atTimeMs(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(IST).toInstant().toEpochMilli();
    }

    private static long startOfDayMs(LocalDate date) {
        return date.atStartOfDay(IST).toInstant().toEpochMilli();
    }

    public record DayBounds(long startMs, long endMs) {
    }

    public record SessionBounds(long preOpenMs, long openMs, long closeMs) {
    }
}
