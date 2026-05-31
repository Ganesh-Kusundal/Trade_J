package com.tradej.historical.ingest.planner;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.model.EquityHistoricalDownloadConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class EquityHistoricalDownloadPlannerTest {

    @Test
    void plansOneTaskPerSymbolForThreeMonthWindow() {
        EquityHistoricalDownloadPlanner planner = new EquityHistoricalDownloadPlanner();
        EquityHistoricalDownloadConfig config = new EquityHistoricalDownloadConfig(
                List.of("TCS", "SBIN"),
                ExchangeSegment.NSE_EQ,
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 5, 30),
                "1m",
                "interval=1m",
                1,
                "data/historical-equity",
                200L,
                8,
                false,
                true
        );
        assertEquals(2, planner.planTasks("job-1", config).size());
        assertEquals(2L, EquityHistoricalDownloadPlanner.estimatedTaskCount(config));
    }

    @Test
    void splitsNinetyOneDayRangeIntoTwoWindowsPerSymbol() {
        EquityHistoricalDownloadConfig config = new EquityHistoricalDownloadConfig(
                List.of("TCS"),
                ExchangeSegment.NSE_EQ,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 5, 30),
                "1m",
                "interval=1m",
                1,
                "data/historical-equity",
                200L,
                8,
                false,
                true
        );
        assertEquals(2, EquityHistoricalDownloadPlanner.splitDateWindows(
                config.fromDate(),
                config.toDate(),
                EquityHistoricalDownloadPlanner.INTRADAY_MAX_DAYS
        ).size());
        assertEquals(2L, EquityHistoricalDownloadPlanner.estimatedTaskCount(config));
    }
}
