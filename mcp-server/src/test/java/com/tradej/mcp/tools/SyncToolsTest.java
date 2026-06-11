package com.tradej.mcp.tools;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SyncToolsTest {

    @Mock
    DataGapScanService gapScanService;

    @Mock
    DownloadJobService downloadJobService;

    SyncTools tools;

    @BeforeEach
    void setUp() {
        tools = new SyncTools(gapScanService, downloadJobService);
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
        assertTrue(output.contains("60"));
        assertTrue(output.contains("Complete: 55"));
        assertTrue(output.contains("Missing: 2"));
        assertTrue(output.contains("2026-06-05"));
        assertTrue(output.contains("2026-06-06"));
    }

    @Test
    void getDataGaps_handlesFullyComplete() {
        var report = new DataGapScanService.GapReport(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 6, 10),
                60, 60, 0, 0,
                List.of(), List.of(), Map.of());
        when(gapScanService.scan(anyString(), anyString(), anyInt())).thenReturn(report);

        String output = tools.getDataGaps(null, null);

        assertTrue(output.contains("Fully complete: true"));
    }

    @Test
    void getDataGaps_handlesNullService() {
        SyncTools noServiceTools = new SyncTools(null, null);

        String output = noServiceTools.getDataGaps("NSE_EQ", 3);

        assertEquals("DataGapScanService not available", output);
    }

    @Test
    void getDownloadJobs_listsRecentJobs() throws Exception {
        var job = new DownloadJobRecord(
                "job-12345678", DownloadSourceType.ROLLING_OPTION,
                DownloadJobStatus.COMPLETED, "{}",
                System.currentTimeMillis() - 3600000,
                System.currentTimeMillis() - 1800000,
                System.currentTimeMillis(),
                null);
        when(downloadJobService.listRecentJobs(5)).thenReturn(List.of(job));
        when(downloadJobService.stats("job-12345678"))
                .thenReturn(new DownloadJobStats(252, 0, 0, 252, 0, 131600));

        String output = tools.getDownloadJobs(5);

        assertNotNull(output);
        assertTrue(output.contains("Recent Download Jobs"));
        assertTrue(output.contains("ROLLING_OPTION"));
        assertTrue(output.contains("COMPLETED"));
        assertTrue(output.contains("252/252"));
    }

    @Test
    void getDownloadJobs_handlesEmptyList() throws Exception {
        when(downloadJobService.listRecentJobs(10)).thenReturn(List.of());

        String output = tools.getDownloadJobs(null);

        assertNotNull(output);
        assertTrue(output.contains("Recent Download Jobs"));
    }

    @Test
    void getDownloadJobs_handlesException() throws Exception {
        when(downloadJobService.listRecentJobs(anyInt()))
                .thenThrow(new RuntimeException("DB error"));

        String output = tools.getDownloadJobs(5);

        assertTrue(output.contains("Failed to list download jobs"));
    }

    @Test
    void getDataGaps_defaultsTo3Months() {
        var report = new DataGapScanService.GapReport(
                LocalDate.now().minusMonths(3), LocalDate.now(),
                60, 60, 0, 0, List.of(), List.of(), Map.of());
        when(gapScanService.scan(eq("NSE_EQ"), eq("1m"), eq(3))).thenReturn(report);

        tools.getDataGaps(null, null);

        verify(gapScanService).scan("NSE_EQ", "1m", 3);
    }
}
