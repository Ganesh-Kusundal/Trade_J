package com.tradej.historical.ingest.calendar;

import com.tradej.core.domain.value.ExchangeSegment;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Trading calendar for Indian exchanges (NSE, BSE, MCX).
 *
 * <p>Determines trading days by combining weekend rules with exchange-specific
 * holiday calendars. Used by gap detection, sync scheduling, and backtesting
 * to distinguish missing data from non-trading days.
 */
public final class TradingCalendarStore {

    private static final Set<LocalDate> NSE_HOLIDAYS_2026 = Set.of(
            // Republic Day
            LocalDate.of(2026, 1, 26),
            // Maha Shivaratri
            LocalDate.of(2026, 2, 17),
            // Holi
            LocalDate.of(2026, 3, 3),
            // Good Friday
            LocalDate.of(2026, 4, 3),
            // Dr. Ambedkar Jayanti
            LocalDate.of(2026, 4, 14),
            // Ram Navami
            LocalDate.of(2026, 4, 27),
            // Maharashtra Day
            LocalDate.of(2026, 5, 1),
            // Eid-ul-Fitr
            LocalDate.of(2026, 3, 21),
            // Buddha Purnima
            LocalDate.of(2026, 5, 12),
            // Bakri Eid (Eid al-Adha — NSE confirmed closed May 28)
            LocalDate.of(2026, 5, 28),
            // Independence Day
            LocalDate.of(2026, 8, 15),
            // Ganesh Chaturthi
            LocalDate.of(2026, 8, 26),
            // Gandhi Jayanti
            LocalDate.of(2026, 10, 2),
            // Dussehra
            LocalDate.of(2026, 10, 20),
            // Diwali (Laxmi Puja — market closed, special Muhurat trading session)
            LocalDate.of(2026, 11, 8),
            // Guru Nanak Jayanti
            LocalDate.of(2026, 11, 24),
            // Christmas
            LocalDate.of(2026, 12, 25)
    );

    private static final Set<LocalDate> NSE_HOLIDAYS_2025 = Set.of(
            LocalDate.of(2025, 1, 26),
            LocalDate.of(2025, 2, 26),
            LocalDate.of(2025, 3, 14),
            LocalDate.of(2025, 4, 18),
            LocalDate.of(2025, 4, 14),
            LocalDate.of(2025, 5, 1),
            LocalDate.of(2025, 8, 15),
            LocalDate.of(2025, 8, 27),
            LocalDate.of(2025, 10, 2),
            LocalDate.of(2025, 10, 21),
            LocalDate.of(2025, 10, 22),
            LocalDate.of(2025, 11, 5),
            LocalDate.of(2025, 11, 26),
            LocalDate.of(2025, 12, 25)
    );

    public boolean isTradingDay(ExchangeSegment segment, LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return segment == ExchangeSegment.MCX_COMM;
        }
        return !isHoliday(segment, date);
    }

    public boolean isHoliday(ExchangeSegment segment, LocalDate date) {
        int year = date.getYear();
        return switch (year) {
            case 2025 -> NSE_HOLIDAYS_2025.contains(date);
            case 2026 -> NSE_HOLIDAYS_2026.contains(date);
            default -> false;
        };
    }

    public List<LocalDate> tradingDays(ExchangeSegment segment, LocalDate from, LocalDate to) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            if (isTradingDay(segment, cursor)) {
                days.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    public LocalDate nextTradingDay(ExchangeSegment segment, LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isTradingDay(segment, next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    public LocalDate previousTradingDay(ExchangeSegment segment, LocalDate date) {
        LocalDate prev = date.minusDays(1);
        while (!isTradingDay(segment, prev)) {
            prev = prev.minusDays(1);
        }
        return prev;
    }

    public int tradingDayCount(ExchangeSegment segment, LocalDate from, LocalDate to) {
        return tradingDays(segment, from, to).size();
    }

    public Set<LocalDate> getHolidays(int year) {
        return switch (year) {
            case 2025 -> NSE_HOLIDAYS_2025;
            case 2026 -> NSE_HOLIDAYS_2026;
            default -> Set.of();
        };
    }
}
