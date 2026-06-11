package com.tradej.mcp.tools;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.historical.ingest.calendar.CompositeHolidayCalendar;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MarketDataTools {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DuckDbAnalyticsEngine engine;
    private final CompositeHolidayCalendar calendar;

    public MarketDataTools(
            DuckDbAnalyticsEngine engine,
            @Autowired(required = false) CompositeHolidayCalendar calendar
    ) {
        this.engine = engine;
        this.calendar = calendar;
    }

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
        List<LocalDate> tradingDays = new java.util.ArrayList<>();
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            if (calendar.isTradingDay(ExchangeSegment.NSE_EQ, cursor)) {
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

        // Universe info
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

        // Bar count and latest date
        LocalDate today = LocalDate.now(IST);
        long fromMs = today.minusYears(5).atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = today.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;

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
}
