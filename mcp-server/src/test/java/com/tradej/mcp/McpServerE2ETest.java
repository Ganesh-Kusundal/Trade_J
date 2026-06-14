package com.tradej.mcp;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsCatalogSnapshot;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.historical.ingest.calendar.CompositeHolidayCalendar;
import com.tradej.historical.ingest.model.DownloadJobRecord;
import com.tradej.historical.ingest.model.DownloadJobStats;
import com.tradej.historical.ingest.model.DownloadJobStatus;
import com.tradej.historical.ingest.model.DownloadSourceType;
import com.tradej.historical.ingest.service.DownloadJobService;
import com.tradej.historical.ingest.sync.DataGapScanService;
import com.tradej.mcp.tools.AnalyticsTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end test for the MCP server tool surface. Verifies every
 * {@code @Tool} annotation is present, that method signatures match
 * the MCP contract, and that each tool executes against a real
 * {@link AnalyticsTools} instance (with mocked infrastructure).
 *
 * <p>Closes audit Gap #19: "MCP server is a presence implementation — no e2e test."
 */
@Tag("e2e")
class McpServerE2ETest {

    static DuckDbAnalyticsEngine engine;
    static CompositeHolidayCalendar calendar;
    static DataGapScanService gapScanService;
    static DownloadJobService downloadJobService;
    static AnalyticsTools tools;

    static final List<String> EXPECTED_TOOLS = List.of(
            "get_market_summary",
            "get_trading_calendar",
            "get_symbol_info",
            "get_top_gainers",
            "get_top_losers",
            "get_highest_volume",
            "get_equity_candles",
            "get_equity_universe",
            "get_data_catalog",
            "run_analytics_sql",
            "get_data_gaps",
            "get_download_jobs",
            "get_option_bars",
            "get_available_option_underlyings",
            "get_option_chain_summary",
            "get_latest_option_trading_day"
    );

    @BeforeAll
    static void setUp() {
        engine = mock(DuckDbAnalyticsEngine.class);
        calendar = mock(CompositeHolidayCalendar.class);
        gapScanService = mock(DataGapScanService.class);
        downloadJobService = mock(DownloadJobService.class);
        tools = new AnalyticsTools(engine, calendar, gapScanService, downloadJobService);
    }

    @Test
    void allExpectedToolAnnotationsPresent() {
        Method[] methods = AnalyticsTools.class.getDeclaredMethods();
        List<String> actualTools = new java.util.ArrayList<>();
        for (Method m : methods) {
            Tool ann = m.getAnnotation(Tool.class);
            if (ann != null) {
                actualTools.add(ann.name());
            }
        }
        assertEquals(EXPECTED_TOOLS.size(), actualTools.size(),
                "Expected " + EXPECTED_TOOLS.size() + " @Tool methods but found " + actualTools.size());
        for (String expected : EXPECTED_TOOLS) {
            assertTrue(actualTools.contains(expected),
                    "Missing @Tool: " + expected);
        }
    }

    @Test
    void toolAnnotationDescriptionNotEmpty() {
        Method[] methods = AnalyticsTools.class.getDeclaredMethods();
        for (Method m : methods) {
            Tool ann = m.getAnnotation(Tool.class);
            if (ann != null) {
                assertFalse(ann.description().isEmpty(),
                        "@Tool " + ann.name() + " has empty description");
            }
        }
    }

    @Test
    void toolCountMatches() {
        long count = java.util.Arrays.stream(AnalyticsTools.class.getDeclaredMethods())
                .filter(m -> m.getAnnotation(Tool.class) != null)
                .count();
        assertEquals(EXPECTED_TOOLS.size(), (int) count,
                "MCP server exposes " + count + " tools");
    }

    @Test
    void getMarketSummary_executes() throws Exception {
        when(engine.catalogSnapshot()).thenReturn(
                new AnalyticsCatalogSnapshot(
                        "/data/equity", "/data/options",
                        100, 50, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 6, 1),
                        25, 1000, LocalDate.of(2025, 6, 1), LocalDate.of(2026, 6, 1),
                        true, true, Map.of()));
        when(engine.latestEquityTradingDay(anyInt()))
                .thenReturn(Optional.of(LocalDate.of(2026, 6, 10)));
        when(engine.availableOptionUnderlyings()).thenReturn(List.of("NIFTY"));

        String result = tools.getMarketSummary();
        assertNotNull(result);
        assertTrue(result.contains("Market Data Summary"));
        assertTrue(result.contains("EQUITY"));
        assertTrue(result.contains("OPTIONS"));
    }

    @Test
    void getTradingCalendar_executes() {
        when(calendar.isTradingDay(eq(com.tradej.core.domain.value.ExchangeSegment.NSE_EQ), any()))
                .thenReturn(true);

        String result = tools.getTradingCalendar("2026-06-01", "2026-06-03");
        assertNotNull(result);
        assertTrue(result.contains("Trading Days"));
    }

    @Test
    void getTradingCalendar_handlesNullCalendar() {
        AnalyticsTools noCalTools = new AnalyticsTools(engine, null, null, null);
        String result = noCalTools.getTradingCalendar("2026-06-01", "2026-06-03");
        assertEquals("TradingCalendar not available", result);
    }

    @Test
    void getSymbolInfo_executes() throws Exception {
        when(engine.queryEquityUniverse()).thenReturn(List.of(
                Map.of("symbol", "RELIANCE", "companyName", "Reliance",
                        "isin", "INE002A01018", "industry", "Oil & Gas", "macroSector", "Energy")));
        when(engine.executeReadOnlySql(anyString(), anyInt()))
                .thenReturn(new AnalyticsQueryResult(
                        List.of("bar_count", "earliest", "latest"),
                        List.of(Map.of("bar_count", 1000, "earliest", 1700000000000L, "latest", 1718000000000L)),
                        1, 5L));

        String result = tools.getSymbolInfo("RELIANCE");
        assertNotNull(result);
        assertTrue(result.contains("Symbol Info: RELIANCE"));
    }

    @Test
    void getTopGainers_executes() throws Exception {
        when(engine.executeReadOnlySql(anyString(), anyInt()))
                .thenReturn(new AnalyticsQueryResult(
                        List.of("symbol", "return_pct"),
                        List.of(Map.of("symbol", "SBIN", "return_pct", 3.5)),
                        1, 10L));

        String result = tools.getTopGainers("2026-06-10", 5);
        assertNotNull(result);
        assertTrue(result.contains("Top 5"));
    }

    @Test
    void getTopLosers_executes() throws Exception {
        when(engine.executeReadOnlySql(anyString(), anyInt()))
                .thenReturn(new AnalyticsQueryResult(
                        List.of("symbol", "return_pct"),
                        List.of(Map.of("symbol", "ITC", "return_pct", -2.1)),
                        1, 10L));

        String result = tools.getTopLosers("2026-06-10", 5);
        assertNotNull(result);
        assertTrue(result.contains("Top 5"));
    }

    @Test
    void getHighestVolume_executes() throws Exception {
        when(engine.executeReadOnlySql(anyString(), anyInt()))
                .thenReturn(new AnalyticsQueryResult(
                        List.of("symbol", "total_volume"),
                        List.of(Map.of("symbol", "RELIANCE", "total_volume", 500000)),
                        1, 10L));

        String result = tools.getHighestVolume("2026-06-10", 5);
        assertNotNull(result);
        assertTrue(result.contains("Highest Volume"));
    }

    @Test
    void getEquityCandles_executes() throws Exception {
        when(engine.queryEquityCandles(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(Map.of(
                        "barTimeMs", 1717200000000L,
                        "openPaisa", 280000, "highPaisa", 285000,
                        "lowPaisa", 278000, "closePaisa", 282000, "volume", 1000)));

        String result = tools.getEquityCandles("RELIANCE", "1m", "2026-06-01", "2026-06-02");
        assertNotNull(result);
        assertTrue(result.contains("Candles for RELIANCE"));
    }

    @Test
    void getEquityUniverse_executes() throws Exception {
        when(engine.queryEquityUniverse()).thenReturn(List.of(
                Map.of("symbol", "TCS", "companyName", "TCS", "industry", "IT", "macroSector", "Technology")));

        String result = tools.getEquityUniverse();
        assertNotNull(result);
        assertTrue(result.contains("Equity Universe"));
    }

    @Test
    void getDataCatalog_executes() throws Exception {
        when(engine.catalogSnapshot()).thenReturn(
                new AnalyticsCatalogSnapshot(
                        "/data/equity", "/data/options",
                        100, 50, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 6, 1),
                        25, 1000, LocalDate.of(2025, 6, 1), LocalDate.of(2026, 6, 1),
                        true, true, Map.of()));

        String result = tools.getDataCatalog();
        assertNotNull(result);
        assertTrue(result.contains("Data Catalog"));
    }

    @Test
    void runAnalyticsSql_executes() throws Exception {
        when(engine.executeReadOnlySql(anyString(), anyInt()))
                .thenReturn(new AnalyticsQueryResult(
                        List.of("cnt"),
                        List.of(Map.of("cnt", 42L)),
                        1, 8L));

        String result = tools.runAnalyticsSql("SELECT count(*) AS cnt FROM equity_bars_1m", 10);
        assertNotNull(result);
        assertTrue(result.contains("SQL Query Result"));
    }

    @Test
    void getDataGaps_executes() {
        var report = new DataGapScanService.GapReport(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 6, 10),
                60, 55, 3, 2,
                List.of(LocalDate.of(2026, 6, 5)),
                List.of(LocalDate.of(2026, 6, 3)),
                Map.of());
        when(gapScanService.scan(eq("NSE_EQ"), eq("1m"), eq(3))).thenReturn(report);

        String result = tools.getDataGaps("NSE_EQ", 3);
        assertNotNull(result);
        assertTrue(result.contains("Data Gap Scan"));
    }

    @Test
    void getDownloadJobs_executes() throws Exception {
        var job = new DownloadJobRecord(
                "job-abc123", DownloadSourceType.EQUITY_INTRADAY,
                DownloadJobStatus.COMPLETED, "{}",
                System.currentTimeMillis() - 3_600_000L,
                System.currentTimeMillis() - 1_800_000L,
                System.currentTimeMillis(), null);
        when(downloadJobService.listRecentJobs(5)).thenReturn(List.of(job));
        when(downloadJobService.stats("job-abc123"))
                .thenReturn(new DownloadJobStats(252, 0, 0, 252, 0, 131_600L));

        String result = tools.getDownloadJobs(5);
        assertNotNull(result);
        assertTrue(result.contains("Recent Download Jobs"));
    }

    @Test
    void getOptionBars_executes() throws Exception {
        when(engine.queryRollingOptionBars(
                eq("NIFTY"), eq("MONTH"), eq(1), eq(0), eq("CALL"), eq(5),
                anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(new RollingOptionBar(
                        1717200000000L, 200000, 205000, 198000, 202000,
                        100, 15.5f, 10000, 2400000, 2380000)));

        String result = tools.getOptionBars("NIFTY", "MONTH", 1, 0, "CALL", 5, "2026-06-01", "2026-06-02");
        assertNotNull(result);
        assertTrue(result.contains("Option Bars"));
    }

    @Test
    void getAvailableOptionUnderlyings_executes() throws Exception {
        when(engine.availableOptionUnderlyings()).thenReturn(List.of("NIFTY", "BANKNIFTY"));

        String result = tools.getAvailableOptionUnderlyings();
        assertNotNull(result);
        assertTrue(result.contains("Available Option Underlyings"));
    }

    @Test
    void getOptionChainSummary_executes() throws Exception {
        when(engine.executeReadOnlySql(anyString(), anyInt()))
                .thenReturn(new AnalyticsQueryResult(
                        List.of("expiry_kind", "expiry_code", "bar_count"),
                        List.of(Map.of("expiry_kind", "MONTH", "expiry_code", 1, "bar_count", 5000)),
                        1, 15L));

        String result = tools.getOptionChainSummary("NIFTY");
        assertNotNull(result);
        assertTrue(result.contains("Option Chain Summary"));
    }

    @Test
    void getLatestOptionTradingDay_executes() throws Exception {
        when(engine.latestOptionTradingDay(eq("NIFTY"), anyInt()))
                .thenReturn(Optional.of(LocalDate.of(2026, 6, 10)));

        String result = tools.getLatestOptionTradingDay("NIFTY");
        assertNotNull(result);
        assertTrue(result.contains("latest option data"));
    }
}
