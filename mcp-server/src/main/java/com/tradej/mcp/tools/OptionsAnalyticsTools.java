package com.tradej.mcp.tools;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.core.domain.model.RollingOptionBar;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Component
public class OptionsAnalyticsTools {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DuckDbAnalyticsEngine engine;

    public OptionsAnalyticsTools(DuckDbAnalyticsEngine engine) {
        this.engine = engine;
    }

    @Tool(name = "get_option_bars",
          description = "Get rolling option bars for a specific contract. Returns OHLCV + IV, OI, spot, strike prices. "
                      + "Prices are in paisa (divide by 100 for rupees). "
                      + "Underlyings: NIFTY, BANKNIFTY, FINNIFTY. Expiry kinds: MONTH, WEEK. "
                      + "Option types: CALL, PUT. Interval: 5 (minutes).")
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
          description = "List all underlying indices that have option bar data in the warehouse. "
                      + "Returns the list of underlyings like NIFTY, BANKNIFTY, FINNIFTY.")
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
          description = "Get a summary of option data available for an underlying index. "
                      + "Shows bar counts and date ranges for each expiry kind, strike offset, and option type.")
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
          description = "Get the latest trading day that has option bar data for a given underlying. "
                      + "Useful to check how current the options data is.")
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
}
