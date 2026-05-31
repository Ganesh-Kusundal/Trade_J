package com.tradej.broker.icici.historical;

import com.tradej.broker.core.historical.HistoricalDateWindowSplitter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class BreezeHistoricalDataServiceWindowingTest {

    @Test
    void splitsFiveDayIntradayRangeIntoDailyWindows() {
        LocalDate start = LocalDate.of(2026, 5, 26);
        LocalDate end = LocalDate.of(2026, 5, 30);
        List<HistoricalDateWindowSplitter.DateWindow> windows =
                HistoricalDateWindowSplitter.split(start, end, 1);
        assertEquals(5, windows.size());
        assertEquals(start, windows.getFirst().fromDate());
        assertEquals(end, windows.getLast().toDate());
    }

    @Test
    void keepsDailyRangeInSingleWindowWhenWithinLimit() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 6, 1);
        List<HistoricalDateWindowSplitter.DateWindow> windows =
                HistoricalDateWindowSplitter.split(start, end, 365);
        assertEquals(1, windows.size());
        assertEquals(start, windows.getFirst().fromDate());
        assertEquals(end, windows.getFirst().toDate());
    }
}
