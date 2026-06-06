package com.tradej.persistence.replay;

import com.tradej.core.domain.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracted candle-loading logic from {@link HistoricalRangeService}.
 *
 * <p>Provides a focused, single-responsibility API for querying completed
 * candles from the DuckDB feature store. This class is designed to be
 * composed independently and can be used as a building block for future
 * refactoring of the HistoricalRangeService.
 */
public final class HistoricalCandleLoader {

    private static final Logger log = LoggerFactory.getLogger(HistoricalCandleLoader.class);

    private static final int DEFAULT_LIMIT = 1000;
    private static final int MAX_LIMIT = 10_000;

    private final Connection connection;

    /**
     * Create a candle loader backed by the given DuckDB connection.
     *
     * @param connection an active DuckDB JDBC connection (caller manages lifecycle)
     */
    public HistoricalCandleLoader(Connection connection) {
        this.connection = connection;
    }

    /**
     * Query completed candles from the feature store within a time range.
     *
     * @param symbol   trading symbol (e.g. "SBIN")
     * @param interval candle interval (e.g. "5m", "1s")
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @param limit    maximum results (capped at {@value MAX_LIMIT})
     * @return list of completed candles in chronological order, empty list on error
     */
    public List<Candle> loadCandles(String symbol, String interval, long fromMs, long toMs, int limit) {
        int cappedLimit = Math.min(limit, MAX_LIMIT);
        try (PreparedStatement ps = connection.prepareStatement("""
                select start_time_ms, end_time_ms, open_paisa, high_paisa, low_paisa,
                       close_paisa, volume
                from feature_candles
                where symbol = ? and interval = ? and closed = true
                  and start_time_ms >= ? and start_time_ms < ?
                order by start_time_ms asc
                limit ?
                """)) {
            ps.setString(1, symbol);
            ps.setString(2, interval);
            ps.setLong(3, fromMs);
            ps.setLong(4, toMs);
            ps.setInt(5, cappedLimit);
            List<Candle> candles = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    candles.add(new Candle(
                            symbol,
                            interval,
                            rs.getLong("start_time_ms"),
                            rs.getLong("end_time_ms"),
                            rs.getLong("open_paisa"),
                            rs.getLong("high_paisa"),
                            rs.getLong("low_paisa"),
                            rs.getLong("close_paisa"),
                            rs.getLong("volume"),
                            true
                    ));
                }
            }
            return candles;
        } catch (SQLException e) {
            log.warn("Failed to load candles for {} {} [{},{}]: {}", symbol, interval, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    /**
     * Short-hand with default limit of {@value DEFAULT_LIMIT}.
     */
    public List<Candle> loadCandles(String symbol, String interval, long fromMs, long toMs) {
        return loadCandles(symbol, interval, fromMs, toMs, DEFAULT_LIMIT);
    }

    /**
     * Count completed candles for a symbol within a time range.
     *
     * @param symbol   trading symbol
     * @param interval candle interval
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @return candle count, or 0 on error
     */
    public long countCandles(String symbol, String interval, long fromMs, long toMs) {
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
                    return rs.getLong("cnt");
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to count candles for {} {} [{},{}]: {}", symbol, interval, fromMs, toMs, e.getMessage());
        }
        return 0L;
    }

    /**
     * Get the timestamp range (first and last) of completed candles for a symbol.
     *
     * @param symbol   trading symbol
     * @param interval candle interval
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @return a two-element long array [firstStartMs, lastStartMs], or [0, 0] if no data
     */
    public long[] candleTimeRange(String symbol, String interval, long fromMs, long toMs) {
        try (PreparedStatement ps = connection.prepareStatement("""
                select min(start_time_ms) as first_ts, max(start_time_ms) as last_ts
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
                    return new long[]{rs.getLong("first_ts"), rs.getLong("last_ts")};
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to get candle time range for {} {} [{},{}]: {}",
                    symbol, interval, fromMs, toMs, e.getMessage());
        }
        return new long[]{0L, 0L};
    }
}
