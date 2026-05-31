package com.tradej.broker.upstox.historical;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class UpstoxHistoricalDataServiceTest {

    @Test
    void splitsLongRangeIntoWindows() {
        List<UpstoxHistoricalDataService.DateWindow> windows = UpstoxHistoricalDataService.splitDateWindows(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 7, 19),
                90
        );
        assertEquals(3, windows.size());
    }

    @Test
    void rejectsUnsupportedFiveMinuteInterval() {
        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> UpstoxHistoricalDataService.validateUpstoxInterval("5minute")
        );
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("not supported"));
    }

    @Test
    void acceptsOneMinuteInterval() {
        UpstoxHistoricalDataService.validateUpstoxInterval("1minute");
    }
}
