package com.tradej.core.domain.value;

import com.tradej.core.domain.time.ExchangeCalendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Trading session schedules for different exchange segments.
 *
 * <p>Centralizes the session-open and session-close times that were previously
 * duplicated across {@code DhanFuturesAdapter} and {@code DhanHistoricalDataClient}.
 */
public final class SessionSchedule {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private static final LocalTime CASH_OPEN = LocalTime.of(9, 15);
    private static final LocalTime CASH_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime MCX_OPEN = LocalTime.of(9, 0);
    private static final LocalTime MCX_CLOSE = LocalTime.of(23, 30);

    private SessionSchedule() {
    }

    /**
     * Returns the India timezone used by all session schedules.
     */
    public static ZoneId indiaZone() {
        return INDIA;
    }

    /**
     * Returns the market open time for the given exchange segment.
     * Delegates to {@link ExchangeCalendar} for per-segment configuration.
     */
    public static LocalTime sessionOpen(ExchangeSegment exchangeSegment) {
        return ExchangeCalendar.sessionHours(exchangeSegment).open();
    }

    /**
     * Returns the market close time for the given exchange segment.
     * Delegates to {@link ExchangeCalendar} for per-segment configuration.
     */
    public static LocalTime sessionClose(ExchangeSegment exchangeSegment) {
        return ExchangeCalendar.sessionHours(exchangeSegment).close();
    }

    /**
     * Returns true if the current time (in India timezone) is past the closing window
     * for the given exchange segment on the given date.
     *
     * <p>If {@code expiry} is today and the current time is past the close,
     * the contract is considered expired for trading purposes (roll-forward).
     * Weekends always return true.
     */
    public static boolean isPastClosingWindow(
            LocalDate expiry,
            ExchangeSegment exchangeSegment,
            LocalDate today,
            LocalTime now
    ) {
        if (expiry == null || !expiry.equals(today)) {
            return false;
        }
        if (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return true;
        }
        return now.isAfter(sessionClose(exchangeSegment));
    }
}
