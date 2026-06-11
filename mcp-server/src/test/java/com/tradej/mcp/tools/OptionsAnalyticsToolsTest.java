package com.tradej.mcp.tools;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.core.domain.model.RollingOptionBar;
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
class OptionsAnalyticsToolsTest {

    @Mock
    DuckDbAnalyticsEngine engine;

    OptionsAnalyticsTools tools;

    @BeforeEach
    void setUp() {
        tools = new OptionsAnalyticsTools(engine);
    }

    @Test
    void getOptionBars_returnsFormattedBars() throws Exception {
        List<RollingOptionBar> bars = List.of(
                new RollingOptionBar(1749504600000L, 46120L, 51820L, 45935L, 48105L,
                        20735L, 13.77, 58370L, 2489295L, 2490000L),
                new RollingOptionBar(1749504900000L, 45690L, 50990L, 45000L, 49320L,
                        32760L, 14.14, 78845L, 2489650L, 2490000L)
        );
        when(engine.queryRollingOptionBars(eq("NIFTY"), eq("MONTH"), eq(1), eq(0),
                eq("CALL"), eq(5), anyLong(), anyLong(), eq(500)))
                .thenReturn(bars);

        String output = tools.getOptionBars("NIFTY", "MONTH", 1, 0, "CALL", 5,
                "2026-06-10", "2026-06-10");

        assertNotNull(output);
        assertTrue(output.contains("Option Bars: NIFTY"));
        assertTrue(output.contains("2 bars"));
        assertTrue(output.contains("MONTH"));
    }

    @Test
    void getOptionBars_handlesEmptyResult() throws Exception {
        when(engine.queryRollingOptionBars(anyString(), anyString(), anyInt(), anyInt(),
                anyString(), anyInt(), anyLong(), anyLong(), anyInt()))
                .thenReturn(List.of());

        String output = tools.getOptionBars("FINNIFTY", "WEEK", 1, 0, "PUT", 5,
                "2026-01-01", "2026-01-01");

        assertNotNull(output);
        assertTrue(output.contains("0 bars"));
    }

    @Test
    void getAvailableOptionUnderlyings_listsUnderlyings() throws Exception {
        when(engine.availableOptionUnderlyings()).thenReturn(List.of("NIFTY", "BANKNIFTY"));

        String output = tools.getAvailableOptionUnderlyings();

        assertNotNull(output);
        assertTrue(output.contains("NIFTY"));
        assertTrue(output.contains("BANKNIFTY"));
        assertTrue(output.contains("2"));
    }

    @Test
    void getAvailableOptionUnderlyings_handlesEmpty() throws Exception {
        when(engine.availableOptionUnderlyings()).thenReturn(List.of());

        String output = tools.getAvailableOptionUnderlyings();

        assertNotNull(output);
        assertTrue(output.contains("0"));
    }

    @Test
    void getOptionChainSummary_returnsBreakdown() throws Exception {
        var result = new AnalyticsQueryResult(
                List.of("expiry_kind", "expiry_code", "strike_offset", "option_type",
                        "interval_min", "bar_count", "earliest", "latest"),
                List.of(
                        Map.of("expiry_kind", "MONTH", "expiry_code", 1, "strike_offset", 0,
                                "option_type", "CALL", "interval_min", 5,
                                "bar_count", 89712L, "earliest", 1740873600000L, "latest", 1748390400000L)
                ),
                1, 15L);
        when(engine.executeReadOnlySql(anyString(), eq(100))).thenReturn(result);

        String output = tools.getOptionChainSummary("NIFTY");

        assertNotNull(output);
        assertTrue(output.contains("Option Chain Summary for NIFTY"));
        assertTrue(output.contains("MONTH"));
    }

    @Test
    void getLatestOptionTradingDay_returnsDate() throws Exception {
        when(engine.latestOptionTradingDay(eq("NIFTY"), eq(365)))
                .thenReturn(Optional.of(LocalDate.of(2026, 6, 10)));

        String output = tools.getLatestOptionTradingDay("NIFTY");

        assertNotNull(output);
        assertTrue(output.contains("2026-06-10"));
        assertTrue(output.contains("days ago"));
    }

    @Test
    void getLatestOptionTradingDay_handlesNoData() throws Exception {
        when(engine.latestOptionTradingDay(eq("FINNIFTY"), eq(365)))
                .thenReturn(Optional.empty());

        String output = tools.getLatestOptionTradingDay("FINNIFTY");

        assertNotNull(output);
        assertTrue(output.contains("No option data found"));
    }
}
