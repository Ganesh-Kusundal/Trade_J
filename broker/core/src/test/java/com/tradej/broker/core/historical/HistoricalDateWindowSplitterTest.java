package com.tradej.broker.core.historical;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class HistoricalDateWindowSplitterTest {

    @Test
    void splitsNinetyDayRangeIntoThreeWindows() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(89);
        List<HistoricalDateWindowSplitter.DateWindow> windows =
                HistoricalDateWindowSplitter.split(start, end, 30);
        assertEquals(3, windows.size());
    }
}
