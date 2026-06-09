package com.tradej.historical.ingest.calendar;

import com.tradej.core.domain.value.ExchangeSegment;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

/**
 * Dynamic holiday calendar that resolves holidays from multiple sources:
 * data-driven (parquet), broker API (Upstox), and hardcoded fallback.
 */
public interface HolidayCalendarService {

    enum HolidaySource {
        DATA, BROKER, HARDCODED
    }

    boolean isHoliday(ExchangeSegment segment, LocalDate date);

    boolean isTradingDay(ExchangeSegment segment, LocalDate date);

    Set<LocalDate> getHolidays(int year);

    HolidaySource getSource(LocalDate date);

    void refreshFromBroker();

    void refreshFromData(Path dataRoot);

    /**
     * Merge externally-discovered holidays (e.g. from broker API).
     */
    void addHolidays(Set<LocalDate> holidays, HolidaySource source);
}
