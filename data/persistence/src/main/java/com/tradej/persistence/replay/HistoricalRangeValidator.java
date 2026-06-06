package com.tradej.persistence.replay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Extracted validation logic from {@link HistoricalRangeService}.
 *
 * <p>Provides focused methods for validating that historical data exists
 * within a given time range before attempting expensive replay operations.
 * This class is designed to be composed independently and can be used as
 * a building block for future refactoring of the HistoricalRangeService.
 */
public final class HistoricalRangeValidator {

    private static final Logger log = LoggerFactory.getLogger(HistoricalRangeValidator.class);

    private final Connection connection;

    /**
     * Create a range validator backed by the given DuckDB connection.
     *
     * @param connection an active DuckDB JDBC connection (caller manages lifecycle)
     */
    public HistoricalRangeValidator(Connection connection) {
        this.connection = connection;
    }

    /**
     * Validate that historical tick data exists for the given symbol and time range.
     *
     * @param symbol trading symbol
     * @param fromMs start of range (inclusive, epoch millis)
     * @param toMs   end of range (exclusive, epoch millis)
     * @return true if at least one tick exists in the range
     */
    public boolean hasTicks(String symbol, long fromMs, long toMs) {
        return countRows("feature_ticks", "exchange_timestamp_ms", symbol, fromMs, toMs) > 0;
    }

    /**
     * Validate that historical candle data exists for the given symbol, interval, and time range.
     *
     * @param symbol   trading symbol
     * @param interval candle interval (e.g. "5m")
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @return true if at least one completed candle exists in the range
     */
    public boolean hasCandles(String symbol, String interval, long fromMs, long toMs) {
        try (PreparedStatement ps = connection.prepareStatement("""
                select count(*) as cnt
                from feature_candles
                where symbol = ? and interval = ? and closed = true
                  and start_time_ms >= ? and start_time_ms < ?
                """)) {
            ps.setString(1, symbol);
            ps.setString(2, interval);
            ps.setLong(3, fromMs);
            ps.setLong(4, toMs);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("cnt") > 0;
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to validate candle data for {} {} [{},{}]: {}",
                    symbol, interval, fromMs, toMs, e.getMessage());
        }
        return false;
    }

    /**
     * Validate that historical order data exists for the given symbol and time range.
     *
     * @param symbol trading symbol (null or empty checks all symbols)
     * @param fromMs start of range (inclusive, epoch millis)
     * @param toMs   end of range (exclusive, epoch millis)
     * @return true if at least one order exists in the range
     */
    public boolean hasOrders(String symbol, long fromMs, long toMs) {
        return countOrderLikeRows("orders", symbol, fromMs, toMs) > 0;
    }

    /**
     * Validate that historical fill data exists for the given symbol and time range.
     *
     * @param symbol trading symbol (null or empty checks all symbols)
     * @param fromMs start of range (inclusive, epoch millis)
     * @param toMs   end of range (exclusive, epoch millis)
     * @return true if at least one fill exists in the range
     */
    public boolean hasFills(String symbol, long fromMs, long toMs) {
        return countOrderLikeRows("fills", symbol, fromMs, toMs) > 0;
    }

    /**
     * Validate that historical fill events exist for the given symbol and time range.
     *
     * @param symbol trading symbol (null or empty checks all symbols)
     * @param fromMs start of range (inclusive, epoch millis)
     * @param toMs   end of range (exclusive, epoch millis)
     * @return true if at least one fill event exists in the range
     */
    public boolean hasFillEvents(String symbol, long fromMs, long toMs) {
        return countOrderLikeRows("fill_events", symbol, fromMs, toMs) > 0;
    }

    /**
     * Validate that historical trade lifecycle events exist for the given symbol and time range.
     *
     * @param symbol trading symbol (null or empty checks all symbols)
     * @param fromMs start of range (inclusive, epoch millis)
     * @param toMs   end of range (exclusive, epoch millis)
     * @return true if at least one trade lifecycle event exists in the range
     */
    public boolean hasTradeLifecycle(String symbol, long fromMs, long toMs) {
        return countOrderLikeRows("trade_lifecycle", symbol, fromMs, toMs) > 0;
    }

    /**
     * Perform a comprehensive check of data availability across all tables.
     *
     * @param symbol trading symbol
     * @param fromMs start of range (inclusive, epoch millis)
     * @param toMs   end of range (exclusive, epoch millis)
     * @return a summary of which data types are available
     */
    public DataAvailability checkAvailability(String symbol, long fromMs, long toMs) {
        boolean ticks = hasTicks(symbol, fromMs, toMs);
        boolean candles = hasCandles(symbol, "5m", fromMs, toMs);
        boolean orders = hasOrders(symbol, fromMs, toMs);
        boolean fills = hasFills(symbol, fromMs, toMs);
        boolean fillEvents = hasFillEvents(symbol, fromMs, toMs);
        boolean tradeLifecycle = hasTradeLifecycle(symbol, fromMs, toMs);
        return new DataAvailability(ticks, candles, orders, fills, fillEvents, tradeLifecycle);
    }

    /**
     * Validate the time range is logically valid (from < to, both non-negative).
     *
     * @param fromMs start of range (inclusive, epoch millis)
     * @param toMs   end of range (exclusive, epoch millis)
     * @throws IllegalArgumentException if the range is invalid
     */
    public static void validateRange(long fromMs, long toMs) {
        if (fromMs < 0) {
            throw new IllegalArgumentException("fromMs must be non-negative, got: " + fromMs);
        }
        if (toMs < 0) {
            throw new IllegalArgumentException("toMs must be non-negative, got: " + toMs);
        }
        if (fromMs >= toMs) {
            throw new IllegalArgumentException("fromMs must be less than toMs, got from=" + fromMs + " to=" + toMs);
        }
    }

    /**
     * Validate that a symbol is non-null and non-blank.
     *
     * @param symbol the symbol to validate
     * @throws IllegalArgumentException if the symbol is null or blank
     */
    public static void validateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be null or blank");
        }
    }

    // ── Private helpers ──

    private long countRows(String table, String timeColumn, String symbol, long fromMs, long toMs) {
        try (PreparedStatement ps = connection.prepareStatement(
                "select count(*) as cnt from " + table
                + " where symbol = ? and " + timeColumn + " >= ? and " + timeColumn + " < ?")) {
            ps.setString(1, symbol);
            ps.setLong(2, fromMs);
            ps.setLong(3, toMs);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("cnt");
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to count rows in {} for {} [{},{}]: {}", table, symbol, fromMs, toMs, e.getMessage());
        }
        return 0L;
    }

    private long countOrderLikeRows(String table, String symbol, long fromMs, long toMs) {
        String sql = "select count(*) as cnt from " + table
                + " where (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?)"
                + " and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)";
        if (symbol != null && !symbol.isBlank()) {
            sql += " and symbol = ?";
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, fromMs);
            ps.setLong(idx++, toMs);
            if (symbol != null && !symbol.isBlank()) {
                ps.setString(idx, symbol);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("cnt");
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to count rows in {} for {} [{},{}]: {}", table, symbol, fromMs, toMs, e.getMessage());
        }
        return 0L;
    }

    /**
     * Summary of which data types are available for a given symbol and time range.
     */
    public record DataAvailability(
            boolean hasTicks,
            boolean hasCandles,
            boolean hasOrders,
            boolean hasFills,
            boolean hasFillEvents,
            boolean hasTradeLifecycle
    ) {
        /** True if any data at all is available. */
        public boolean hasAnyData() {
            return hasTicks || hasCandles || hasOrders || hasFills || hasFillEvents || hasTradeLifecycle;
        }

        /** True if tick or candle data is available (enough for market replay). */
        public boolean hasMarketData() {
            return hasTicks || hasCandles;
        }

        /** True if order/fill data is available (enough for execution replay). */
        public boolean hasExecutionData() {
            return hasOrders || hasFills || hasFillEvents;
        }
    }
}
