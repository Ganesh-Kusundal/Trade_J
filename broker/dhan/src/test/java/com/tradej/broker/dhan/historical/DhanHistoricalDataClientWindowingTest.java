package com.tradej.broker.dhan.historical;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class DhanHistoricalDataClientWindowingTest {

    @Test
    void splitsLongRangeIntoFixedSizeWindows() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 12, 31);
        List<?> windows = DhanHistoricalDataClient.splitDateWindows(from, to, 90);
        assertEquals(5, windows.size());
    }

    @Test
    void keepsSingleWindowWhenRangeFitsLimit() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        List<?> windows = DhanHistoricalDataClient.splitDateWindows(from, to, 90);
        assertEquals(1, windows.size());
    }

    @Test
    void rejectsInvalidDateOrder() {
        assertThrows(IllegalArgumentException.class, () ->
                DhanHistoricalDataClient.splitDateWindows(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1),
                        90
                ));
    }

    @Test
    void defaultsToNinetyDayRangeWhenDatesMissing() {
        DhanHistoricalDataClient.DateWindow range =
                DhanHistoricalDataClient.resolveDateRange(null, null);
        assertNotNull(range);
        long daysInclusive = java.time.temporal.ChronoUnit.DAYS.between(range.fromDate(), range.toDate()) + 1L;
        assertEquals(90L, daysInclusive);
    }

    @Test
    void defaultsFromDateWhenOnlyToDateProvided() {
        LocalDate to = LocalDate.of(2026, 5, 28);
        DhanHistoricalDataClient.DateWindow range =
                DhanHistoricalDataClient.resolveDateRange(null, to);
        assertEquals(to, range.toDate());
        assertEquals(to.minusDays(89), range.fromDate());
    }

    @Test
    void intradayWindowingUsesNinetyDayCeiling() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 7, 19); // 200 days inclusive
        List<DhanHistoricalDataClient.DateWindow> windows =
                DhanHistoricalDataClient.splitDateWindows(from, to, 90);
        assertEquals(3, windows.size());
        assertEquals(LocalDate.of(2025, 1, 1), windows.get(0).fromDate());
        assertEquals(LocalDate.of(2025, 3, 31), windows.get(0).toDate());
        assertEquals(LocalDate.of(2025, 4, 1), windows.get(1).fromDate());
        assertEquals(LocalDate.of(2025, 6, 29), windows.get(1).toDate());
        assertEquals(LocalDate.of(2025, 6, 30), windows.get(2).fromDate());
        assertEquals(LocalDate.of(2025, 7, 19), windows.get(2).toDate());
    }

    @Test
    void dailyWindowingSupportsLargeRangesWithinSingleRequest() {
        LocalDate from = LocalDate.of(2020, 1, 1);
        LocalDate to = LocalDate.of(2025, 12, 31);
        List<DhanHistoricalDataClient.DateWindow> windows =
                DhanHistoricalDataClient.splitDateWindows(from, to, 3650);
        assertEquals(1, windows.size());
        assertEquals(from, windows.get(0).fromDate());
        assertEquals(to, windows.get(0).toDate());
    }
}
