package com.tradej.mcp.tools;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsCatalogSnapshot;
import com.tradej.core.domain.model.AnalyticsQueryResult;
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
class EquityAnalyticsToolsTest {

    @Mock
    DuckDbAnalyticsEngine engine;

    EquityAnalyticsTools tools;

    @BeforeEach
    void setUp() {
        tools = new EquityAnalyticsTools(engine);
    }

    @Test
    void getTopGainers_returnsFormattedResult() throws Exception {
        var result = new AnalyticsQueryResult(
                List.of("symbol", "return_pct", "total_volume"),
                List.of(
                        Map.of("symbol", "RELIANCE", "return_pct", 5.23, "total_volume", 1000000L),
                        Map.of("symbol", "TCS", "return_pct", 3.14, "total_volume", 500000L)
                ),
                2, 10L);
        when(engine.executeReadOnlySql(anyString(), eq(10))).thenReturn(result);

        String output = tools.getTopGainers("2026-06-10", 10);

        assertNotNull(output);
        assertTrue(output.contains("Top 10 Gainers for 2026-06-10"));
        assertTrue(output.contains("RELIANCE"));
        assertTrue(output.contains("TCS"));
        verify(engine).executeReadOnlySql(anyString(), eq(10));
    }

    @Test
    void getTopLosers_returnsFormattedResult() throws Exception {
        var result = new AnalyticsQueryResult(
                List.of("symbol", "return_pct"),
                List.of(Map.of("symbol", "SBIN", "return_pct", -4.5)),
                1, 5L);
        when(engine.executeReadOnlySql(anyString(), eq(5))).thenReturn(result);

        String output = tools.getTopLosers("2026-06-10", 5);

        assertNotNull(output);
        assertTrue(output.contains("Top 5 Losers"));
        assertTrue(output.contains("SBIN"));
    }

    @Test
    void getHighestVolume_returnsFormattedResult() throws Exception {
        var result = new AnalyticsQueryResult(
                List.of("symbol", "total_volume"),
                List.of(Map.of("symbol", "JPPOWER", "total_volume", 435000000L)),
                1, 8L);
        when(engine.executeReadOnlySql(anyString(), eq(10))).thenReturn(result);

        String output = tools.getHighestVolume("2026-06-10", null);

        assertNotNull(output);
        assertTrue(output.contains("Highest Volume"));
        assertTrue(output.contains("JPPOWER"));
    }

    @Test
    void getEquityCandles_returnsFormattedBars() throws Exception {
        when(engine.queryEquityCandles(eq("RELIANCE"), anyLong(), anyLong(), eq(500)))
                .thenReturn(List.of(
                        Map.of("barTimeMs", 1749504600000L, "openPaisa", 126900L,
                                "highPaisa", 127500L, "lowPaisa", 126800L,
                                "closePaisa", 127200L, "volume", 84000L)
                ));

        String output = tools.getEquityCandles("RELIANCE", "1m", "2026-06-10", "2026-06-10");

        assertNotNull(output);
        assertTrue(output.contains("Candles for RELIANCE"));
        assertTrue(output.contains("1 bars"));
    }

    @Test
    void getEquityCandles_handlesEmptyResult() throws Exception {
        when(engine.queryEquityCandles(eq("UNKNOWN"), anyLong(), anyLong(), eq(500)))
                .thenReturn(List.of());

        String output = tools.getEquityCandles("UNKNOWN", "1m", "2026-06-10", "2026-06-10");

        assertNotNull(output);
        assertTrue(output.contains("0 bars"));
    }

    @Test
    void getEquityUniverse_returnsFormattedSymbols() throws Exception {
        when(engine.queryEquityUniverse()).thenReturn(List.of(
                Map.of("symbol", "RELIANCE", "companyName", "Reliance Industries",
                        "industry", "Oil & Gas", "macroSector", "Energy")
        ));

        String output = tools.getEquityUniverse();

        assertNotNull(output);
        assertTrue(output.contains("1 symbols"));
        assertTrue(output.contains("RELIANCE"));
    }

    @Test
    void getDataCatalog_returnsCoverageSummary() throws Exception {
        var snapshot = new AnalyticsCatalogSnapshot(
                "data/historical-equity", "runtime-dev/historical.duckdb",
                499L, 30886L, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 6, 10),
                499L, 1023301L, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 6, 10),
                true, true, Map.of("equity_bars_1m", "Parquet equity bars"));
        when(engine.catalogSnapshot()).thenReturn(snapshot);

        String output = tools.getDataCatalog();

        assertNotNull(output);
        assertTrue(output.contains("499"));
        assertTrue(output.contains("30886"));
        assertTrue(output.contains("Options attached: true"));
    }

    @Test
    void runAnalyticsSql_executesAndFormatsQuery() throws Exception {
        var result = new AnalyticsQueryResult(
                List.of("count"),
                List.of(Map.of("count", 30886L)),
                1, 3L);
        when(engine.executeReadOnlySql(eq("SELECT count(*) FROM equity_bars_1m"), eq(100)))
                .thenReturn(result);

        String output = tools.runAnalyticsSql("SELECT count(*) FROM equity_bars_1m", null);

        assertNotNull(output);
        assertTrue(output.contains("SQL Query Result"));
        assertTrue(output.contains("1 rows"));
    }

    @Test
    void getTopGainers_defaultsLimitTo10() throws Exception {
        var result = new AnalyticsQueryResult(List.of("symbol"), List.of(), 0, 1L);
        when(engine.executeReadOnlySql(anyString(), eq(10))).thenReturn(result);

        tools.getTopGainers("2026-06-10", null);

        verify(engine).executeReadOnlySql(anyString(), eq(10));
    }
}
