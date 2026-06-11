package com.tradej.mcp.tools;

import com.tradej.analytics.config.DuckDbAnalyticsConfig;
import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

@Tag("integration")
class McpToolsLiveTest {

    static DuckDbAnalyticsEngine engine;
    static EquityAnalyticsTools equityTools;
    static OptionsAnalyticsTools optionsTools;
    static MarketDataTools marketTools;

    @BeforeAll
    static void setUp() {
        String workspaceRoot = System.getProperty("trade.workspace.root", ".");
        Path equityRoot = Path.of(workspaceRoot).resolve("data/historical-equity");
        Path optionsWarehouse = Path.of(workspaceRoot).resolve("runtime-dev/historical.duckdb");
        Path runtimeDb = Path.of(workspaceRoot).resolve("runtime-dev/trade.duckdb");

        engine = new DuckDbAnalyticsEngine(new DuckDbAnalyticsConfig(
                equityRoot,
                optionsWarehouse,
                runtimeDb,
                true,
                true,
                10000,
                30000L
        ));
        equityTools = new EquityAnalyticsTools(engine);
        optionsTools = new OptionsAnalyticsTools(engine);
        marketTools = new MarketDataTools(engine, null);
    }

    @AfterAll
    static void tearDown() {
        if (engine != null) engine.close();
    }

    @Test
    void marketSummary() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: get_market_summary");
        System.out.println("=".repeat(80));
        System.out.println(marketTools.getMarketSummary());
    }

    @Test
    void dataCatalog() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: get_data_catalog");
        System.out.println("=".repeat(80));
        System.out.println(equityTools.getDataCatalog());
    }

    @Test
    void niftyOptionUnderlyings() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: get_available_option_underlyings");
        System.out.println("=".repeat(80));
        System.out.println(optionsTools.getAvailableOptionUnderlyings());
    }

    @Test
    void niftyLatestOptionDay() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: get_latest_option_trading_day(NIFTY)");
        System.out.println("=".repeat(80));
        System.out.println(optionsTools.getLatestOptionTradingDay("NIFTY"));
    }

    @Test
    void niftyOptionChainSummary() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: get_option_chain_summary(NIFTY)");
        System.out.println("=".repeat(80));
        System.out.println(optionsTools.getOptionChainSummary("NIFTY"));
    }

    @Test
    void niftyOptionBarsToday() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: get_option_bars(NIFTY, WEEK, 1, 0, CALL, 5, today)");
        System.out.println("=".repeat(80));
        System.out.println(optionsTools.getOptionBars("NIFTY", "WEEK", 1, 0, "CALL", 5,
                "2026-06-10", "2026-06-10"));
    }

    @Test
    void niftyOptionBarsTodayPut() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: get_option_bars(NIFTY, WEEK, 1, 0, PUT, 5, today)");
        System.out.println("=".repeat(80));
        System.out.println(optionsTools.getOptionBars("NIFTY", "WEEK", 1, 0, "PUT", 5,
                "2026-06-10", "2026-06-10"));
    }

    @Test
    void bankNiftyOptionChainSummary() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: get_option_chain_summary(BANKNIFTY)");
        System.out.println("=".repeat(80));
        System.out.println(optionsTools.getOptionChainSummary("BANKNIFTY"));
    }

    @Test
    void topGainersToday() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: get_top_gainers(2026-06-10, 10)");
        System.out.println("=".repeat(80));
        System.out.println(equityTools.getTopGainers("2026-06-10", 10));
    }

    @Test
    void analyticsSqlOptionAnalysis() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: run_analytics_sql (NIFTY option IV analysis today)");
        System.out.println("=".repeat(80));
        String sql = """
                SELECT
                    option_type,
                    strike_offset,
                    round(avg(iv), 2) AS avg_iv,
                    round(min(iv), 2) AS min_iv,
                    round(max(iv), 2) AS max_iv,
                    sum(volume) AS total_volume,
                    sum(oi) AS total_oi,
                    count(*) AS bar_count
                FROM rolling_option_bars
                WHERE underlying = 'NIFTY'
                  AND bar_time_ms >= 1749504600000
                  AND bar_time_ms < 1749591000000
                GROUP BY option_type, strike_offset
                ORDER BY option_type, strike_offset
                """;
        System.out.println(equityTools.runAnalyticsSql(sql, 50));
    }

    @Test
    void analyticsSqlNiftyMaxPain() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MCP TOOL: run_analytics_sql (NIFTY OI by strike — max pain proxy)");
        System.out.println("=".repeat(80));
        String sql = """
                SELECT
                    strike_offset,
                    option_type,
                    arg_max(oi, bar_time_ms) AS latest_oi,
                    arg_max(spot_paisa, bar_time_ms) AS latest_spot,
                    arg_max(strike_paisa, bar_time_ms) AS strike_price
                FROM rolling_option_bars
                WHERE underlying = 'NIFTY'
                  AND expiry_kind = 'WEEK'
                  AND expiry_code = 1
                  AND bar_time_ms >= 1749504600000
                  AND bar_time_ms < 1749591000000
                GROUP BY strike_offset, option_type
                ORDER BY latest_oi DESC
                """;
        System.out.println(equityTools.runAnalyticsSql(sql, 20));
    }
}
