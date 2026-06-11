package com.tradej.mcp.tools;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.core.domain.model.Candle;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EquityAnalyticsTools {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DuckDbAnalyticsEngine engine;

    public EquityAnalyticsTools(DuckDbAnalyticsEngine engine) {
        this.engine = engine;
    }

    @Tool(name = "get_top_gainers",
          description = "Get top gaining stocks for a given date, ranked by daily return percentage. "
                      + "Returns symbol, open/close prices, return %, volume, high/low for each stock.")
    public String getTopGainers(
            @ToolParam(description = "Date in yyyy-MM-dd format, e.g. 2026-06-10") String date,
            @ToolParam(description = "Number of top gainers to return, default 10") Integer limit
    ) throws Exception {
        int rowLimit = limit != null ? limit : 10;
        LocalDate d = LocalDate.parse(date);
        long fromMs = d.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = d.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;

        String sql = """
                SELECT symbol,
                       arg_min(open_paisa, bar_time_ms) AS open_price,
                       arg_max(close_paisa, bar_time_ms) AS close_price,
                       round((arg_max(close_paisa, bar_time_ms) - arg_min(open_paisa, bar_time_ms)) * 100.0
                             / NULLIF(arg_min(open_paisa, bar_time_ms), 0), 2) AS return_pct,
                       sum(volume) AS total_volume,
                       max(high_paisa) AS day_high,
                       min(low_paisa) AS day_low
                FROM equity_bars_1m
                WHERE bar_time_ms >= %d AND bar_time_ms < %d
                GROUP BY symbol
                HAVING arg_min(open_paisa, bar_time_ms) > 0 AND count(*) > 100
                ORDER BY return_pct DESC
                """.formatted(fromMs, toMs);

        AnalyticsQueryResult result = engine.executeReadOnlySql(sql, rowLimit);
        return formatResult("Top " + rowLimit + " Gainers for " + date, result);
    }

    @Tool(name = "get_top_losers",
          description = "Get top losing stocks for a given date, ranked by daily return percentage (most negative first). "
                      + "Returns symbol, open/close prices, return %, volume.")
    public String getTopLosers(
            @ToolParam(description = "Date in yyyy-MM-dd format") String date,
            @ToolParam(description = "Number of top losers to return, default 10") Integer limit
    ) throws Exception {
        int rowLimit = limit != null ? limit : 10;
        LocalDate d = LocalDate.parse(date);
        long fromMs = d.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = d.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;

        String sql = """
                SELECT symbol,
                       arg_min(open_paisa, bar_time_ms) AS open_price,
                       arg_max(close_paisa, bar_time_ms) AS close_price,
                       round((arg_max(close_paisa, bar_time_ms) - arg_min(open_paisa, bar_time_ms)) * 100.0
                             / NULLIF(arg_min(open_paisa, bar_time_ms), 0), 2) AS return_pct,
                       sum(volume) AS total_volume
                FROM equity_bars_1m
                WHERE bar_time_ms >= %d AND bar_time_ms < %d
                GROUP BY symbol
                HAVING arg_min(open_paisa, bar_time_ms) > 0 AND count(*) > 100
                ORDER BY return_pct ASC
                """.formatted(fromMs, toMs);

        AnalyticsQueryResult result = engine.executeReadOnlySql(sql, rowLimit);
        return formatResult("Top " + rowLimit + " Losers for " + date, result);
    }

    @Tool(name = "get_highest_volume",
          description = "Get stocks with highest trading volume for a given date. "
                      + "Returns symbol, total volume, open/close prices, bar count.")
    public String getHighestVolume(
            @ToolParam(description = "Date in yyyy-MM-dd format") String date,
            @ToolParam(description = "Number of results, default 10") Integer limit
    ) throws Exception {
        int rowLimit = limit != null ? limit : 10;
        LocalDate d = LocalDate.parse(date);
        long fromMs = d.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = d.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;

        String sql = """
                SELECT symbol,
                       sum(volume) AS total_volume,
                       arg_min(open_paisa, bar_time_ms) AS open_price,
                       arg_max(close_paisa, bar_time_ms) AS close_price,
                       count(*) AS bar_count
                FROM equity_bars_1m
                WHERE bar_time_ms >= %d AND bar_time_ms < %d
                GROUP BY symbol
                ORDER BY total_volume DESC
                """.formatted(fromMs, toMs);

        AnalyticsQueryResult result = engine.executeReadOnlySql(sql, rowLimit);
        return formatResult("Highest Volume for " + date, result);
    }

    @Tool(name = "get_equity_candles",
          description = "Get OHLCV candle data for a specific stock symbol and date range. "
                      + "Returns bar_time_ms, open, high, low, close prices (in paisa), and volume. "
                      + "Prices are in paisa (divide by 100 for rupees).")
    public String getEquityCandles(
            @ToolParam(description = "Stock symbol, e.g. RELIANCE, SBIN, TCS") String symbol,
            @ToolParam(description = "Interval: 1m, 5m, 15m, 1h, 1d") String interval,
            @ToolParam(description = "Start date in yyyy-MM-dd format") String from,
            @ToolParam(description = "End date in yyyy-MM-dd format") String to
    ) throws Exception {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        long fromMs = fromDate.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = toDate.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;

        List<Map<String, Object>> rows = engine.queryEquityCandles(symbol, fromMs, toMs, 500);
        StringBuilder sb = new StringBuilder();
        sb.append("Candles for ").append(symbol).append(" (").append(interval).append(") ")
          .append(from).append(" to ").append(to).append(": ").append(rows.size()).append(" bars\n\n");

        if (!rows.isEmpty()) {
            sb.append(String.format("%-20s %10s %10s %10s %10s %12s%n",
                    "Time", "Open", "High", "Low", "Close", "Volume"));
            for (Map<String, Object> row : rows.subList(0, Math.min(50, rows.size()))) {
                sb.append(String.format("%-20s %10s %10s %10s %10s %12s%n",
                        row.get("barTimeMs"), row.get("openPaisa"), row.get("highPaisa"),
                        row.get("lowPaisa"), row.get("closePaisa"), row.get("volume")));
            }
            if (rows.size() > 50) {
                sb.append("... and ").append(rows.size() - 50).append(" more rows");
            }
        }
        return sb.toString();
    }

    @Tool(name = "get_equity_universe",
          description = "List all stock symbols in the equity universe with metadata. "
                      + "Returns symbol, company name, ISIN, industry, macro sector.")
    public String getEquityUniverse() throws Exception {
        List<Map<String, Object>> rows = engine.queryEquityUniverse();
        StringBuilder sb = new StringBuilder();
        sb.append("Equity Universe: ").append(rows.size()).append(" symbols\n\n");
        sb.append(String.format("%-18s %-30s %-15s %-20s%n", "Symbol", "Company", "Industry", "Sector"));
        for (Map<String, Object> row : rows.subList(0, Math.min(50, rows.size()))) {
            sb.append(String.format("%-18s %-30s %-15s %-20s%n",
                    row.get("symbol"),
                    truncate(String.valueOf(row.get("companyName")), 28),
                    truncate(String.valueOf(row.get("industry")), 13),
                    truncate(String.valueOf(row.get("macroSector")), 18)));
        }
        if (rows.size() > 50) {
            sb.append("... and ").append(rows.size() - 50).append(" more symbols");
        }
        return sb.toString();
    }

    @Tool(name = "get_data_catalog",
          description = "Get data catalog summary showing equity and options data coverage. "
                      + "Returns symbol count, bar file count, date range, options bar count, and available views.")
    public String getDataCatalog() throws Exception {
        var snapshot = engine.catalogSnapshot();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Data Catalog ===\n");
        sb.append("Equity root: ").append(snapshot.equityRoot()).append("\n");
        sb.append("Symbols: ").append(snapshot.equitySymbolCount()).append("\n");
        sb.append("Bar files: ").append(snapshot.equityBarFileCount()).append("\n");
        sb.append("Date range: ").append(snapshot.equityMinMonth()).append(" to ").append(snapshot.equityMaxMonth()).append("\n");
        sb.append("Universe rows: ").append(snapshot.universeRowCount()).append("\n");
        sb.append("Options attached: ").append(snapshot.optionsAttached()).append("\n");
        sb.append("Options bars: ").append(snapshot.optionsBarCount()).append("\n");
        sb.append("Options range: ").append(snapshot.optionsMinDate()).append(" to ").append(snapshot.optionsMaxDate()).append("\n");
        sb.append("Runtime attached: ").append(snapshot.runtimeAttached()).append("\n");
        sb.append("\nViews:\n");
        snapshot.views().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }

    @Tool(name = "run_analytics_sql",
          description = "Run a read-only SQL query against the DuckDB analytics engine. "
                      + "Available tables/views: equity_bars_1m (parquet), equity_universe, rolling_option_bars. "
                      + "Only SELECT queries are allowed. Use for custom analytics not covered by other tools.")
    public String runAnalyticsSql(
            @ToolParam(description = "SQL SELECT query to execute") String sql,
            @ToolParam(description = "Maximum rows to return, default 100") Integer limit
    ) throws Exception {
        int rowLimit = limit != null ? limit : 100;
        AnalyticsQueryResult result = engine.executeReadOnlySql(sql, rowLimit);
        return formatResult("SQL Query Result (" + result.rows().size() + " rows, " + result.elapsedMs() + "ms)", result);
    }

    private String formatResult(String title, AnalyticsQueryResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n\n");
        if (result.columns().isEmpty() || result.rows().isEmpty()) {
            sb.append("No data returned.");
            return sb.toString();
        }
        sb.append(String.join(" | ", result.columns())).append("\n");
        sb.append("-".repeat(Math.min(120, result.columns().size() * 15))).append("\n");
        for (Map<String, Object> row : result.rows()) {
            for (String col : result.columns()) {
                sb.append(String.format("%-15s", row.get(col)));
                sb.append(" | ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 2) + "..";
    }
}
