package com.tradej.broker.core.historical;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class HistoricalDateWindowSplitter {

    private HistoricalDateWindowSplitter() {
    }

    public record DateWindow(LocalDate fromDate, LocalDate toDate) {
    }

    public static List<DateWindow> split(LocalDate fromDate, LocalDate toDate, int maxDaysPerRequest) {
        if (maxDaysPerRequest <= 0) {
            throw new IllegalArgumentException("maxDaysPerRequest must be positive");
        }
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Invalid historical date range");
        }
        List<DateWindow> windows = new ArrayList<>();
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            LocalDate windowEnd = cursor.plusDays(maxDaysPerRequest - 1L);
            if (windowEnd.isAfter(toDate)) {
                windowEnd = toDate;
            }
            windows.add(new DateWindow(cursor, windowEnd));
            cursor = windowEnd.plusDays(1L);
        }
        return windows;
    }
}
