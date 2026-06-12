package com.tradej.mcp.tools;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.historical.ingest.calendar.CompositeHolidayCalendar;
import com.tradej.historical.ingest.model.DownloadJobRecord;
import com.tradej.historical.ingest.model.DownloadJobStats;
import com.tradej.historical.ingest.model.DownloadJobStatus;
import com.tradej.historical.ingest.model.DownloadSourceType;
import com.tradej.historical.ingest.service.DownloadJobService;
import com.tradej.historical.ingest.sync.DataGapScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Smoke test for the consolidated {@link AnalyticsTools} class. The original
 * four tool classes (MarketDataTools, SyncTools, EquityAnalyticsTools,
 * OptionsAnalyticsTools) were merged here as part of the platform's
 * delete-first review; the test surface was merged with them.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AnalyticsToolsTest {

    @Mock DuckDbAnalyticsEngine engine;
    @Mock CompositeHolidayCalendar calendar;
    @Mock DataGapScanService gapScanService;
    @Mock DownloadJobService downloadJobService;

    AnalyticsTools tools;

    @BeforeEach
    void setUp() {
        tools = new AnalyticsTools(engine, calendar, gapScanService, downloadJobService);
    }

    @Test
    void getDataGaps_returnsScanReport() {
        var report = new DataGapScanService.GapReport(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 6, 10),
                60, 55, 3, 2,
                List.of(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 6)),
                List.of(LocalDate.of(2026, 6, 3)),
                Map.of());
        when(gapScanService.scan(eq("NSE_EQ"), eq("1m"), eq(3))).thenReturn(report);

        String output = tools.getDataGaps("NSE_EQ", 3);

        assertNotNull(output);
        assertTrue(output.contains("Data Gap Scan"));
        assertTrue(output.contains("Missing: 2"));
        assertTrue(output.contains("2026-06-05"));
    }

    @Test
    void getDataGaps_handlesNullService() {
        AnalyticsTools noServiceTools = new AnalyticsTools(engine, null, null, null);

        String output = noServiceTools.getDataGaps("NSE_EQ", 3);

        assertEquals("DataGapScanService not available", output);
    }

    @Test
    void getDownloadJobs_listsRecentJobs() throws Exception {
        var job = new DownloadJobRecord(
                "job-12345678", DownloadSourceType.ROLLING_OPTION,
                DownloadJobStatus.COMPLETED, "{}",
                System.currentTimeMillis() - 3_600_000L,
                System.currentTimeMillis() - 1_800_000L,
                System.currentTimeMillis(),
                null);
        when(downloadJobService.listRecentJobs(5)).thenReturn(List.of(job));
        when(downloadJobService.stats("job-12345678"))
                .thenReturn(new DownloadJobStats(252, 0, 0, 252, 0, 131_600L));

        String output = tools.getDownloadJobs(5);

        assertNotNull(output);
        assertTrue(output.contains("Recent Download Jobs"));
        assertTrue(output.contains("job-12345678"));
    }

    @Test
    void runAnalyticsSql_returnsRows() throws Exception {
        when(engine.executeReadOnlySql(anyString(), anyInt()))
                .thenReturn(new AnalyticsQueryResult(
                        List.of("symbol", "return_pct"),
                        List.of(Map.of("symbol", "SBIN", "return_pct", 1.5)),
                        1,
                        10L));

        String output = tools.runAnalyticsSql("SELECT symbol FROM equity_bars_1m", 50);

        assertTrue(output.contains("SBIN"));
        assertTrue(output.contains("10ms"));
    }
}
