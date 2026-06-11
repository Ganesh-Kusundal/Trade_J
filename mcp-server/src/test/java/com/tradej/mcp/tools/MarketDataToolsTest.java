package com.tradej.mcp.tools;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsCatalogSnapshot;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.historical.ingest.calendar.CompositeHolidayCalendar;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketDataToolsTest {

    @Mock
    DuckDbAnalyticsEngine engine;

    @Mock
    CompositeHolidayCalendar calendar;

    MarketDataTools tools;

    @BeforeEach
    void setUp() {
        tools = new MarketDataTools(engine, calendar);
    }

    @Test
    void getMarketSummary_returnsCombinedStatus() throws Exception {
        var snapshot = new AnalyticsCatalogSnapshot(
                "data/historical-equity", "runtime-dev/historical.duckdb",
                499L, 30886L, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 6, 10),
                499L, 1023301L, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 6, 10),
                true, true, Map.of());
        when(engine.catalogSnapshot()).thenReturn(snapshot);
        when(engine.latestEquityTradingDay(365)).thenReturn(Optional.of(LocalDate.of(2026, 6, 10)));
        when(engine.availableOptionUnderlyings()).thenReturn(List.of("NIFTY", "BANKNIFTY"));

        String output = tools.getMarketSummary();

        assertNotNull(output);
        assertTrue(output.contains("Market Data Summary"));
        assertTrue(output.contains("499"));
        assertTrue(output.contains("OPTIONS"));
        assertTrue(output.contains("NIFTY"));
        assertTrue(output.contains("BANKNIFTY"));
    }

    @Test
    void getMarketSummary_handlesNoOptions() throws Exception {
        var snapshot = new AnalyticsCatalogSnapshot(
                "data/historical-equity", "/dev/null",
                499L, 30886L, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 6, 10),
                499L, 0L, null, null,
                false, false, Map.of());
        when(engine.catalogSnapshot()).thenReturn(snapshot);
        when(engine.latestEquityTradingDay(365)).thenReturn(Optional.empty());

        String output = tools.getMarketSummary();

        assertNotNull(output);
        assertTrue(output.contains("Attached: false"));
    }

    @Test
    void getTradingCalendar_listsTradingDays() {
        when(calendar.isTradingDay(eq(ExchangeSegment.NSE_EQ), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    LocalDate d = inv.getArgument(1);
                    return d.getDayOfWeek().getValue() <= 5; // weekdays
                });

        String output = tools.getTradingCalendar("2026-06-08", "2026-06-12");

        assertNotNull(output);
        assertTrue(output.contains("Trading Days"));
        // June 8 (Mon) to June 12 (Fri) = 5 trading days
        assertTrue(output.contains("Count: 5"));
    }

    @Test
    void getTradingCalendar_excludesWeekends() {
        when(calendar.isTradingDay(eq(ExchangeSegment.NSE_EQ), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    LocalDate d = inv.getArgument(1);
                    return d.getDayOfWeek().getValue() <= 5;
                });

        String output = tools.getTradingCalendar("2026-06-06", "2026-06-08");

        // June 6 (Sat), June 7 (Sun), June 8 (Mon) = 1 trading day
        assertTrue(output.contains("Count: 1"));
    }

    @Test
    void getTradingCalendar_handlesNullCalendar() {
        MarketDataTools noCalTools = new MarketDataTools(engine, null);

        String output = noCalTools.getTradingCalendar("2026-06-01", "2026-06-10");

        assertEquals("TradingCalendar not available", output);
    }

    @Test
    void getSymbolInfo_returnsDetails() throws Exception {
        when(engine.queryEquityUniverse()).thenReturn(List.of(
                Map.of("symbol", "RELIANCE", "companyName", "Reliance Industries Ltd",
                        "isin", "INE002A01018", "industry", "Oil & Gas", "macroSector", "Energy")
        ));
        var stats = new AnalyticsQueryResult(
                List.of("bar_count", "earliest", "latest"),
                List.of(Map.of("bar_count", 30000L, "earliest", 1577836800000L, "latest", 1749504600000L)),
                1, 5L);
        when(engine.executeReadOnlySql(anyString(), eq(1))).thenReturn(stats);

        String output = tools.getSymbolInfo("RELIANCE");

        assertNotNull(output);
        assertTrue(output.contains("Symbol Info: RELIANCE"));
        assertTrue(output.contains("Reliance Industries"));
        assertTrue(output.contains("Oil & Gas"));
        assertTrue(output.contains("30000"));
    }

    @Test
    void getSymbolInfo_handlesUnknownSymbol() throws Exception {
        when(engine.queryEquityUniverse()).thenReturn(List.of());
        var stats = new AnalyticsQueryResult(List.of("bar_count"), List.of(), 0, 1L);
        when(engine.executeReadOnlySql(anyString(), eq(1))).thenReturn(stats);

        String output = tools.getSymbolInfo("UNKNOWN");

        assertNotNull(output);
        assertTrue(output.contains("Not found in universe"));
    }
}
