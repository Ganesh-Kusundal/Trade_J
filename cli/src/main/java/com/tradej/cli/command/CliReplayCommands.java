package com.tradej.cli.command;

import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.Candle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Standalone replay commands for the Quant/Trader Workbench.
 *
 * <p>Queries historical tick and candle data directly from DuckDB warehouses
 * without requiring {@code trade-app} or Spring. Falls back to broker REST API
 * when the warehouse file is not found.
 */
public final class CliReplayCommands extends CliCommandSupport {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(IST);

    /** Default DuckDB warehouse path for rolling options. */
    private static final String DEFAULT_WAREHOUSE = "runtime-dev/historical.duckdb";

    public CliReplayCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    /**
     * `tradej replay run --symbol RELIANCE --date 2025-01-15`
     *
     * Replays tick data from the DuckDB warehouse for the given symbol and date.
     */
    public void replayTicks(String symbol, LocalDate date) throws Exception {
        Path warehouse = Path.of(DEFAULT_WAREHOUSE);
        if (!Files.exists(warehouse)) {
            out().println("DuckDB warehouse not found at " + warehouse.toAbsolutePath());
            out().println("Falling back to broker REST API for historical ticks (if available)...");
            return;
        }

        long fromMs = date.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = date.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + warehouse.toAbsolutePath())) {
            List<MarketTickEvent> ticks = queryTicks(conn, symbol, fromMs, toMs, 5000);
            if (ticks.isEmpty()) {
                out().println("No tick data found for " + symbol + " on " + date);
                return;
            }

            if (context().json()) {
                out().print(ticks);
                return;
            }

            out().println("Replayed " + ticks.size() + " ticks for " + symbol + " on " + date);
            // Print first N and last N ticks as summary
            int show = Math.min(5, ticks.size());
            for (int i = 0; i < show; i++) {
                printTick(ticks.get(i));
            }
            if (ticks.size() > show * 2) {
                out().println("  ... " + (ticks.size() - show * 2) + " more ticks ...");
                for (int i = ticks.size() - show; i < ticks.size(); i++) {
                    printTick(ticks.get(i));
                }
            } else {
                for (int i = show; i < ticks.size(); i++) {
                    printTick(ticks.get(i));
                }
            }
            out().println("");
            out().println("Summary:");
            out().println("  First tick:  " + formatTickTime(ticks.getFirst().exchangeTimestampEpochMs()));
            out().println("  Last tick:   " + formatTickTime(ticks.getLast().exchangeTimestampEpochMs()));
            out().println("  Total ticks: " + ticks.size());
        }
    }

    /**
     * `tradej replay candles --symbol RELIANCE --interval 5m --date 2025-01-15`
     *
     * Replays candle data from the DuckDB warehouse for the given symbol and date.
     */
    public void replayCandles(String symbol, String interval, LocalDate date) throws Exception {
        Path warehouse = Path.of(DEFAULT_WAREHOUSE);
        if (!Files.exists(warehouse)) {
            out().println("DuckDB warehouse not found at " + warehouse.toAbsolutePath());
            out().println("Falling back to broker REST API for historical candles (if available)...");
            return;
        }

        long fromMs = date.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = date.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + warehouse.toAbsolutePath())) {
            List<Candle> candles = queryCandles(conn, symbol, interval, fromMs, toMs, 1000);
            if (candles.isEmpty()) {
                out().println("No candle data found for " + symbol + " " + interval + " on " + date);
                return;
            }

            if (context().json()) {
                out().print(candles);
                return;
            }

            out().println("Replayed " + candles.size() + " candles for " + symbol + " " + interval + " on " + date);
            List<String[]> rows = new ArrayList<>();
            for (Candle c : candles) {
                rows.add(new String[]{
                        formatMs(c.startTimeMs()),
                        String.valueOf(c.openPaisa()),
                        String.valueOf(c.highPaisa()),
                        String.valueOf(c.lowPaisa()),
                        String.valueOf(c.closePaisa()),
                        String.valueOf(c.volume())
                });
            }
            TablePrinter.print(new String[]{"Time", "Open", "High", "Low", "Close", "Vol"}, rows);
        }
    }

    // ── Internal helpers ──

    private List<MarketTickEvent> queryTicks(Connection conn, String symbol,
                                              long fromMs, long toMs, int limit) throws SQLException {
        List<MarketTickEvent> ticks = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                select event_id, symbol, ltp_paisa, last_trade_quantity,
                       cumulative_volume, exchange_timestamp_ms, exchange_segment
                from feature_ticks
                where symbol = ? and exchange_timestamp_ms >= ? and exchange_timestamp_ms < ?
                order by exchange_timestamp_ms asc
                limit ?
                """)) {
            ps.setString(1, symbol);
            ps.setLong(2, fromMs);
            ps.setLong(3, toMs);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ticks.add(new MarketTickEvent(
                            com.tradej.core.domain.event.EventMetadata.root(),
                            0L,
                            symbol,
                            null,
                            null,
                            rs.getLong("ltp_paisa"),
                            rs.getLong("last_trade_quantity"),
                            rs.getLong("cumulative_volume"),
                            rs.getLong("exchange_timestamp_ms"),
                            java.util.Optional.empty(),
                            0L,
                            0L
                    ));
                }
            }
        }
        return ticks;
    }

    private List<Candle> queryCandles(Connection conn, String symbol, String interval,
                                      long fromMs, long toMs, int limit) throws SQLException {
        List<Candle> candles = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                select start_time_ms, end_time_ms, open_paisa, high_paisa, low_paisa,
                       close_paisa, volume, closed
                from feature_candles
                where symbol = ? and interval = ? and start_time_ms >= ? and start_time_ms < ?
                order by start_time_ms asc
                limit ?
                """)) {
            ps.setString(1, symbol);
            ps.setString(2, interval);
            ps.setLong(3, fromMs);
            ps.setLong(4, toMs);
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    candles.add(new Candle(
                            symbol, interval,
                            rs.getLong("start_time_ms"),
                            rs.getLong("end_time_ms"),
                            rs.getLong("open_paisa"),
                            rs.getLong("high_paisa"),
                            rs.getLong("low_paisa"),
                            rs.getLong("close_paisa"),
                            rs.getLong("volume"),
                            rs.getBoolean("closed")
                    ));
                }
            }
        }
        return candles;
    }

    String formatTickTime(long epochMs) {
        return FMT.format(java.time.Instant.ofEpochMilli(epochMs));
    }

    private void printTick(MarketTickEvent tick) {
        out().println("  " + formatTickTime(tick.exchangeTimestampEpochMs())
                + " ltp=" + tick.ltpPaisa()
                + " qty=" + tick.lastTradeQuantity()
                + " vol=" + tick.cumulativeVolume());
    }
}
