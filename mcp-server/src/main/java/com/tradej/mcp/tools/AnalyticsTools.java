package com.tradej.mcp.tools;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.historical.ingest.calendar.CompositeHolidayCalendar;
import com.tradej.historical.ingest.service.DownloadJobService;
import com.tradej.historical.ingest.sync.DataGapScanService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single MCP tool surface for the Trade-J analytics + sync + market data + options
 * stack. Replaces the four old tool classes
 * ({@code MarketDataTools}, {@code SyncTools}, {@code EquityAnalyticsTools},
 * {@code OptionsAnalyticsTools}) so that all {@code @Tool} methods are
 * discoverable in one place.
 *
 * <p>12 tools are exposed:
 * <ul>
 *   <li>market: {@code get_market_summary}, {@code get_trading_calendar}, {@code get_symbol_info}</li>
 *   <li>equity: {@code get_top_gainers}, {@code get_top_losers}, {@code get_highest_volume},
 *       {@code get_equity_candles}, {@code get_equity_universe}, {@code get_data_catalog}, {@code run_analytics_sql}</li>
 *   <li>sync: {@code get_data_gaps}, {@code get_download_jobs}</li>
 *   <li>options: {@code get_option_bars}, {@code get_available_option_underlyings},
 *       {@code get_option_chain_summary}, {@code get_latest_option_trading_day}</li>
 * </ul>
 *
 * (Total exposed = 17, grouped above for clarity.)
 */
@Component
public class AnalyticsTools {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DuckDbAnalyticsEngine engine;
    private final CompositeHolidayCalendar calendar;
    private final DataGapScanService gapScanService;
    private final DownloadJobService downloadJobService;

    public AnalyticsTools(
            DuckDbAnalyticsEngine engine,
            @Autowired(required = false) CompositeHolidayCalendar calendar,
            @Autowired(required = false) DataGapScanService gapScanService,
            @Autowired(required = false) DownloadJobService downloadJobService
    ) {
        this.engine = engine;
        this.calendar = calendar;
        this.gapScanService = gapScanService;
        this.downloadJobService = downloadJobService;
    }

    // ── Market ───────────────────────────────────────────────────────────

    @Tool(name = "get_market_summary",
          description = "Get a combined summary of all market data available: equity data coverage "
                      + "(symbols, date range, bar count), options data coverage (underlyings, bar count, date range), "
                      + "and latest trading day for each asset class.")
    public String getMarketSummary() throws Exception {
        var catalog = engine.catalogSnapshot();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Market Data Summary ===\n\n");

        sb.append("EQUITY\n");
        sb.append("  Symbols: ").append(catalog.equitySymbolCount()).append("\n");
        sb.append("  Bar files: ").append(catalog.equityBarFileCount()).append("\n");
        sb.append("  Date range: ").append(catalog.equityMinMonth()).append(" to ").append(catalog.equityMaxMonth()).append("\n");

        Optional<LocalDate> latestEquity = engine.latestEquityTradingDay(365);
        if (latestEquity.isPresent()) {
            long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(latestEquity.get(), LocalDate.now(IST));
            sb.append("  Latest trading day: ").append(latestEquity.get()).append(" (").append(daysAgo).append(" days ago)\n");
        }

        sb.append("\nOPTIONS\n");
        sb.append("  Attached: ").append(catalog.optionsAttached()).append("\n");
        if (catalog.optionsAttached()) {
            sb.append("  Bars: ").append(catalog.optionsBarCount()).append("\n");
            sb.append("  Date range: ").append(catalog.optionsMinDate()).append(" to ").append(catalog.optionsMaxDate()).append("\n");

            List<String> underlyings = engine.availableOptionUnderlyings();
            sb.append("  Underlyings: ").append(String.join(", ", underlyings)).append("\n");
        }

        sb.append("\nRUNTIME\n");
        sb.append("  Attached: ").append(catalog.runtimeAttached()).append("\n");

        return sb.toString();
    }

    @Tool(name = "get_trading_calendar",
          description = "Get the list of trading days in a date range, accounting for weekends and holidays. "
                      + "Useful to know which dates have market data available.")
    public String getTradingCalendar(
            @ToolParam(description = "Start date yyyy-MM-dd") String from,
            @ToolParam(description = "End date yyyy-MM-dd") String to
    ) {
        if (calendar == null) {
            return "TradingCalendar not available";
        }
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        List<LocalDate> tradingDays = new ArrayList<>();
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            if (calendar.isTradingDay(com.tradej.core.domain.value.ExchangeSegment.NSE_EQ, cursor)) {
                tradingDays.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Trading Days: ").append(from).append(" to ").append(to).append("\n");
        sb.append("Count: ").append(tradingDays.size()).append("\n\n");
        for (LocalDate d : tradingDays) {
            sb.append("  ").append(d).append(" (").append(d.getDayOfWeek().name().substring(0, 3)).append(")\n");
        }
        return sb.toString();
    }

    @Tool(name = "get_symbol_info",
          description = "Get detailed information about a specific stock symbol: company name, industry, "
                      + "sector, latest available bar date, and total bar count in the warehouse.")
    public String getSymbolInfo(
            @ToolParam(description = "Stock symbol, e.g. RELIANCE, SBIN, TCS") String symbol
    ) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Symbol Info: ").append(symbol).append(" ===\n\n");

        List<Map<String, Object>> universe = engine.queryEquityUniverse();
        Optional<Map<String, Object>> entry = universe.stream()
                .filter(u -> symbol.equalsIgnoreCase(String.valueOf(u.get("symbol"))))
                .findFirst();

        if (entry.isPresent()) {
            Map<String, Object> e = entry.get();
            sb.append("Company: ").append(e.get("companyName")).append("\n");
            sb.append("ISIN: ").append(e.get("isin")).append("\n");
            sb.append("Industry: ").append(e.get("industry")).append("\n");
            sb.append("Sector: ").append(e.get("macroSector")).append("\n");
        } else {
            sb.append("Not found in universe\n");
        }

        LocalDate today = LocalDate.now(IST);
        String sql = """
                SELECT count(*) as bar_count,
                       min(bar_time_ms) as earliest,
                       max(bar_time_ms) as latest
                FROM equity_bars_1m
                WHERE symbol = '%s'
                """.formatted(symbol);

        AnalyticsQueryResult stats = engine.executeReadOnlySql(sql, 1);
        if (!stats.rows().isEmpty()) {
            Map<String, Object> row = stats.rows().getFirst();
            sb.append("\nBar count: ").append(row.get("bar_count")).append("\n");
            sb.append("Earliest: ").append(row.get("earliest")).append("\n");
            sb.append("Latest: ").append(row.get("latest")).append("\n");
        }

        return sb.toString();
    }

    // ── Equity analytics ─────────────────────────────────────────────────

    @Tool(name = "get_top_gainers",
          description = "Get top gaining stocks for a given date, ranked by daily return percentage. "
                      + "Returns symbol, open/close prices, return %, volume, high/low for each stock.")
    public String getTopGainers(
            @ToolParam(description = "Date in yyyy-MM-dd format, e.g. 2026-06-10") String date,
            @ToolParam(description = "Number of top gainers to return, default 10") Integer limit
    ) throws Exception {
        return runDateRangeQuery("get_top_gainers", date, limit, "DESC");
    }

    @Tool(name = "get_top_losers",
          description = "Get top losing stocks for a given date, ranked by daily return percentage (most negative first).")
    public String getTopLosers(
            @ToolParam(description = "Date in yyyy-MM-dd format") String date,
            @ToolParam(description = "Number of top losers to return, default 10") Integer limit
    ) throws Exception {
        return runDateRangeQuery("get_top_losers", date, limit, "ASC");
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
          description = "Get OHLCV candle data for a specific stock symbol and date range.")
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
          description = "List all stock symbols in the equity universe with metadata.")
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
          description = "Get data catalog summary showing equity and options data coverage.")
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
                      + "Only SELECT queries are allowed.")
    public String runAnalyticsSql(
            @ToolParam(description = "SQL SELECT query to execute") String sql,
            @ToolParam(description = "Maximum rows to return, default 100") Integer limit
    ) throws Exception {
        int rowLimit = limit != null ? limit : 100;
        AnalyticsQueryResult result = engine.executeReadOnlySql(sql, rowLimit);
        return formatResult("SQL Query Result (" + result.rows().size() + " rows, " + result.elapsedMs() + "ms)", result);
    }

    // ── Sync ─────────────────────────────────────────────────────────────

    @Tool(name = "get_data_gaps",
          description = "Scan for missing or partial data gaps in the equity parquet warehouse.")
    public String getDataGaps(
            @ToolParam(description = "Exchange segment: NSE_EQ") String segment,
            @ToolParam(description = "Lookback in months, default 3") Integer lookbackMonths
    ) {
        if (gapScanService == null) {
            return "DataGapScanService not available";
        }
        int months = lookbackMonths != null ? lookbackMonths : 3;
        var report = gapScanService.scan(segment != null ? segment : "NSE_EQ", "1m", months);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Data Gap Scan ===\n");
        sb.append("Range: ").append(report.scanFrom()).append(" to ").append(report.scanTo()).append("\n");
        sb.append("Trading days: ").append(report.totalTradingDays()).append("\n");
        sb.append("Complete: ").append(report.daysComplete()).append("\n");
        sb.append("Partial: ").append(report.daysPartial()).append("\n");
        sb.append("Missing: ").append(report.daysMissing()).append("\n");
        sb.append("Fully complete: ").append(report.isFullyComplete()).append("\n");
        if (!report.missingDates().isEmpty()) {
            sb.append("\nMissing dates:\n");
            report.missingDates().forEach(d -> sb.append("  ").append(d).append("\n"));
        }
        if (!report.partialDates().isEmpty()) {
            sb.append("\nPartial dates:\n");
            report.partialDates().forEach(d -> sb.append("  ").append(d).append("\n"));
        }
        return sb.toString();
    }

    @Tool(name = "get_download_jobs",
          description = "List recent download jobs (equity and options).")
    public String getDownloadJobs(
            @ToolParam(description = "Number of recent jobs to show, default 10") Integer limit
    ) {
        if (downloadJobService == null) {
            return "DownloadJobService not available";
        }
        int rowLimit = limit != null ? limit : 10;
        try {
            var jobs = downloadJobService.listRecentJobs(rowLimit);
            StringBuilder sb = new StringBuilder();
            sb.append("=== Recent Download Jobs ===\n\n");
            for (var job : jobs) {
                sb.append("Job: ").append(job.jobId()).append("\n");
                sb.append("  Type: ").append(job.sourceType()).append("\n");
                sb.append("  Status: ").append(job.status()).append("\n");
                sb.append("  Created: ").append(job.createdAtMs()).append("\n");
                if (job.finishedAtMs() != null) {
                    sb.append("  Finished: ").append(job.finishedAtMs()).append("\n");
                }
                try {
                    var stats = downloadJobService.stats(job.jobId());
                    sb.append("  Tasks: ").append(stats.completedTasks()).append("/")
                      .append(stats.totalTasks()).append(" completed, ")
                      .append(stats.rowsWritten()).append(" rows\n");
                } catch (Exception ignored) {}
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Failed to list download jobs: " + ex.getMessage();
        }
    }

    // ── Options ──────────────────────────────────────────────────────────

    @Tool(name = "get_option_bars",
          description = "Get rolling option bars for a specific contract. Returns OHLCV + IV, OI, spot, strike prices.")
    public String getOptionBars(
            @ToolParam(description = "Underlying index: NIFTY, BANKNIFTY, FINNIFTY") String underlying,
            @ToolParam(description = "Expiry kind: MONTH or WEEK") String expiryKind,
            @ToolParam(description = "Expiry code: 1 for nearest, 2 for next") int expiryCode,
            @ToolParam(description = "Strike offset from ATM: 0=ATM, 1=1 strike OTM, -1=1 strike ITM") int strikeOffset,
            @ToolParam(description = "Option type: CALL or PUT") String optionType,
            @ToolParam(description = "Interval in minutes: 5") int intervalMin,
            @ToolParam(description = "Start date yyyy-MM-dd") String from,
            @ToolParam(description = "End date yyyy-MM-dd") String to
    ) throws Exception {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        long fromMs = fromDate.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = toDate.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;

        List<RollingOptionBar> bars = engine.queryRollingOptionBars(
                underlying, expiryKind, expiryCode, strikeOffset, optionType, intervalMin, fromMs, toMs, 500);

        StringBuilder sb = new StringBuilder();
        sb.append("Option Bars: ").append(underlying).append(" ").append(expiryKind).append(" exp=")
          .append(expiryCode).append(" strike=").append(strikeOffset).append(" ").append(optionType)
          .append(" ").append(intervalMin).append("m\n");
        sb.append(from).append(" to ").append(to).append(": ").append(bars.size()).append(" bars\n\n");

        sb.append(String.format("%-20s %10s %10s %10s %10s %10s %8s %10s %10s %10s%n",
                "Time", "Open", "High", "Low", "Close", "Volume", "IV", "OI", "Spot", "Strike"));
        for (RollingOptionBar bar : bars.subList(0, Math.min(50, bars.size()))) {
            sb.append(String.format("%-20d %10d %10d %10d %10d %10d %8.2f %10d %10d %10d%n",
                    bar.timestampMs(), bar.openPaisa(), bar.highPaisa(), bar.lowPaisa(), bar.closePaisa(),
                    bar.volume(), bar.iv(), bar.oi(), bar.spotPaisa(), bar.strikePaisa()));
        }
        if (bars.size() > 50) {
            sb.append("... and ").append(bars.size() - 50).append(" more bars");
        }
        return sb.toString();
    }

    @Tool(name = "get_available_option_underlyings",
          description = "List all underlying indices that have option bar data in the warehouse.")
    public String getAvailableOptionUnderlyings() throws Exception {
        List<String> underlyings = engine.availableOptionUnderlyings();
        StringBuilder sb = new StringBuilder();
        sb.append("Available Option Underlyings: ").append(underlyings.size()).append("\n\n");
        for (String u : underlyings) {
            sb.append("  ").append(u).append("\n");
        }
        return sb.toString();
    }

    @Tool(name = "get_option_chain_summary",
          description = "Get a summary of option data available for an underlying index.")
    public String getOptionChainSummary(
            @ToolParam(description = "Underlying index: NIFTY, BANKNIFTY, FINNIFTY") String underlying
    ) throws Exception {
        String sql = """
                SELECT expiry_kind, expiry_code, strike_offset, option_type, interval_min,
                       count(*) as bar_count,
                       min(bar_time_ms) as earliest,
                       max(bar_time_ms) as latest
                FROM rolling_option_bars
                WHERE underlying = '%s'
                GROUP BY expiry_kind, expiry_code, strike_offset, option_type, interval_min
                ORDER BY expiry_kind, expiry_code, strike_offset, option_type
                """.formatted(underlying);

        AnalyticsQueryResult result = engine.executeReadOnlySql(sql, 100);

        StringBuilder sb = new StringBuilder();
        sb.append("Option Chain Summary for ").append(underlying).append("\n\n");
        sb.append(String.format("%-8s %5s %7s %5s %5s %8s %-12s %-12s%n",
                "Expiry", "Code", "Strike", "Type", "Intv", "Bars", "Earliest", "Latest"));
        for (var row : result.rows()) {
            sb.append(String.format("%-8s %5s %7s %5s %5s %8s %-12s %-12s%n",
                    row.get("expiry_kind"),
                    row.get("expiry_code"),
                    row.get("strike_offset"),
                    row.get("option_type"),
                    row.get("interval_min"),
                    row.get("bar_count"),
                    row.get("earliest"),
                    row.get("latest")));
        }
        return sb.toString();
    }

    @Tool(name = "get_latest_option_trading_day",
          description = "Get the latest trading day that has option bar data for a given underlying.")
    public String getLatestOptionTradingDay(
            @ToolParam(description = "Underlying index: NIFTY, BANKNIFTY, FINNIFTY") String underlying
    ) throws Exception {
        Optional<LocalDate> latest = engine.latestOptionTradingDay(underlying, 365);
        if (latest.isEmpty()) {
            return "No option data found for " + underlying;
        }
        LocalDate d = latest.get();
        long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(d, LocalDate.now(IST));
        return underlying + " latest option data: " + d + " (" + daysAgo + " days ago)";
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private String runDateRangeQuery(String title, String date, Integer limit, String order) throws Exception {
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
                ORDER BY return_pct %s
                """.formatted(fromMs, toMs, order);

        AnalyticsQueryResult result = engine.executeReadOnlySql(sql, rowLimit);
        return formatResult("Top " + rowLimit + " for " + date + " (" + title + ")", result);
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
