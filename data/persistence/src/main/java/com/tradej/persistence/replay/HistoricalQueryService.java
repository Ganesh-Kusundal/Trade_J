package com.tradej.persistence.replay;

import com.tradej.core.domain.event.MarketTickEvent;
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
 * Read-only query service for historical data stored in DuckDB.
 *
 * <p>Provides SQL-based retrieval of candles, ticks, orders, fills, fill events,
 * trade lifecycle events, and range statistics. No event-bus publishing — this is
 * a pure data-access layer.
 *
 * <p>Extracted from {@link HistoricalRangeService} to enforce SRP: queries belong
 * here, replay/event-publishing belongs in {@link HistoricalEventReplayService}.
 */
public final class HistoricalQueryService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalQueryService.class);

    private final Connection connection;

    public HistoricalQueryService(Connection connection) {
        this.connection = connection;
    }

    // ── Candle queries ──

    public List<Candle> queryCandles(String symbol, String interval, long fromMs, long toMs, int limit) {
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
            ps.setInt(5, Math.min(limit, 10_000));
            List<Candle> candles = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    candles.add(new Candle(symbol, interval,
                            rs.getLong("start_time_ms"), rs.getLong("end_time_ms"),
                            rs.getLong("open_paisa"), rs.getLong("high_paisa"),
                            rs.getLong("low_paisa"), rs.getLong("close_paisa"),
                            rs.getLong("volume"), true));
                }
            }
            return candles;
        } catch (SQLException e) {
            log.warn("Failed to query candles for {} {} [{},{}]: {}", symbol, interval, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    public List<Candle> queryCandles(String symbol, String interval, long fromMs, long toMs) {
        return queryCandles(symbol, interval, fromMs, toMs, 1000);
    }

    // ── Tick queries ──

    public List<MarketTickEvent> queryTicks(String symbol, long fromMs, long toMs, int limit) {
        try (PreparedStatement ps = connection.prepareStatement("""
                select event_id, interval, ltp_paisa, last_trade_quantity,
                       cumulative_volume, exchange_timestamp_ms, exchange_segment
                from feature_ticks
                where symbol = ? and exchange_timestamp_ms >= ? and exchange_timestamp_ms < ?
                order by exchange_timestamp_ms asc
                limit ?
                """)) {
            ps.setString(1, symbol);
            ps.setLong(2, fromMs);
            ps.setLong(3, toMs);
            ps.setInt(4, Math.min(limit, 50_000));
            List<MarketTickEvent> ticks = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ticks.add(new MarketTickEvent(
                            com.tradej.core.domain.event.EventMetadata.correlated("", 0L),
                            0L, symbol,
                            com.tradej.core.domain.value.ExchangeSegment.NSE_EQ,
                            com.tradej.core.domain.value.FeedMode.TICKER,
                            rs.getLong("ltp_paisa"), rs.getLong("last_trade_quantity"),
                            rs.getLong("cumulative_volume"), rs.getLong("exchange_timestamp_ms"),
                            java.util.Optional.empty(), 0L, 0L));
                }
            }
            return ticks;
        } catch (SQLException e) {
            log.warn("Failed to query ticks for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    public List<MarketTickEvent> queryTicks(String symbol, long fromMs, long toMs) {
        return queryTicks(symbol, fromMs, toMs, 5000);
    }

    // ── Order queries ──

    public List<HistoricalRangeService.HistoricalOrder> queryOrders(String symbol, long fromMs, long toMs, int limit) {
        String sql = """
                select event_id, order_id, correlation_id, symbol, status, quantity, price_paisa
                from orders
                where (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?) and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """ + (symbol != null && !symbol.isBlank() ? " and symbol = ?" : "") + """
                order by coalesce(event_time_ms, ingested_at_ms) desc nulls last, event_id asc
                limit ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, fromMs);
            ps.setLong(idx++, toMs);
            if (symbol != null && !symbol.isBlank()) ps.setString(idx++, symbol);
            ps.setInt(idx, Math.min(limit, 5000));
            List<HistoricalRangeService.HistoricalOrder> orders = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(new HistoricalRangeService.HistoricalOrder(
                            rs.getString("event_id"), rs.getString("order_id"),
                            rs.getString("correlation_id"), rs.getString("symbol"),
                            rs.getString("status"), rs.getLong("quantity"), rs.getLong("price_paisa")));
                }
            }
            return orders;
        } catch (SQLException e) {
            log.warn("Failed to query orders for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    public List<HistoricalRangeService.HistoricalOrder> queryOrders(String symbol, long fromMs, long toMs) {
        return queryOrders(symbol, fromMs, toMs, 500);
    }

    // ── Fill queries ──

    public List<HistoricalRangeService.HistoricalFill> queryFills(String symbol, long fromMs, long toMs, int limit) {
        String sql = """
                select event_id, order_id, trade_id, symbol, quantity, price_paisa
                from fills
                where (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?) and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """ + (symbol != null && !symbol.isBlank() ? " and symbol = ?" : "") + """
                order by coalesce(event_time_ms, ingested_at_ms) desc nulls last, event_id asc
                limit ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, fromMs);
            ps.setLong(idx++, toMs);
            if (symbol != null && !symbol.isBlank()) ps.setString(idx++, symbol);
            ps.setInt(idx, Math.min(limit, 5000));
            List<HistoricalRangeService.HistoricalFill> fills = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fills.add(new HistoricalRangeService.HistoricalFill(
                            rs.getString("event_id"), rs.getString("order_id"),
                            rs.getString("trade_id"), rs.getString("symbol"),
                            rs.getLong("quantity"), rs.getLong("price_paisa")));
                }
            }
            return fills;
        } catch (SQLException e) {
            log.warn("Failed to query fills for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    public List<HistoricalRangeService.HistoricalFill> queryFills(String symbol, long fromMs, long toMs) {
        return queryFills(symbol, fromMs, toMs, 500);
    }

    // ── Fill-event queries ──

    public List<HistoricalRangeService.HistoricalFillEvent> queryFillEvents(String symbol, long fromMs, long toMs, int limit) {
        String sql = """
                select event_id, event_type, order_id, correlation_id, symbol,
                       quantity, price_paisa, fill_count
                from fill_events
                where (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?) and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """ + (symbol != null && !symbol.isBlank() ? " and symbol = ?" : "") + """
                order by coalesce(event_time_ms, ingested_at_ms) desc nulls last, event_id asc
                limit ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, fromMs);
            ps.setLong(idx++, toMs);
            if (symbol != null && !symbol.isBlank()) ps.setString(idx++, symbol);
            ps.setInt(idx, Math.min(limit, 5000));
            List<HistoricalRangeService.HistoricalFillEvent> events = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new HistoricalRangeService.HistoricalFillEvent(
                            rs.getString("event_id"), rs.getString("event_type"),
                            rs.getString("order_id"), rs.getString("correlation_id"),
                            rs.getString("symbol"), rs.getLong("quantity"),
                            rs.getLong("price_paisa"), rs.getInt("fill_count")));
                }
            }
            return events;
        } catch (SQLException e) {
            log.warn("Failed to query fill events for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    public List<HistoricalRangeService.HistoricalFillEvent> queryFillEvents(String symbol, long fromMs, long toMs) {
        return queryFillEvents(symbol, fromMs, toMs, 500);
    }

    // ── Trade-lifecycle queries ──

    public List<HistoricalRangeService.HistoricalTradeEvent> queryTradeLifecycle(String symbol, long fromMs, long toMs, int limit) {
        String sql = """
                select event_id, event_type, trade_id, order_id, signal_id,
                       symbol, side, size, entry_price_paisa,
                       stop_loss_paisa, take_profit_paisa,
                       exit_price_paisa, realized_pnl_paisa, close_reason
                from trade_lifecycle
                where (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?)
                  and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """ + (symbol != null && !symbol.isBlank() ? " and symbol = ?" : "") + """
                order by coalesce(event_time_ms, ingested_at_ms) asc nulls last, event_id asc
                limit ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, fromMs);
            ps.setLong(idx++, toMs);
            if (symbol != null && !symbol.isBlank()) ps.setString(idx++, symbol);
            ps.setInt(idx, Math.min(limit, 50_000));
            List<HistoricalRangeService.HistoricalTradeEvent> events = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new HistoricalRangeService.HistoricalTradeEvent(
                            rs.getString("event_id"), rs.getString("event_type"),
                            rs.getString("trade_id"), rs.getString("order_id"),
                            rs.getString("signal_id"), rs.getString("symbol"),
                            rs.getString("side"), rs.getLong("size"),
                            rs.getLong("entry_price_paisa"), rs.getLong("stop_loss_paisa"),
                            rs.getLong("take_profit_paisa"), rs.getLong("exit_price_paisa"),
                            rs.getLong("realized_pnl_paisa"), rs.getString("close_reason")));
                }
            }
            return events;
        } catch (SQLException e) {
            log.warn("Failed to query trade lifecycle for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    public List<HistoricalRangeService.HistoricalTradeEvent> queryTradeLifecycle(long fromMs, long toMs) {
        return queryTradeLifecycle(null, fromMs, toMs, 5000);
    }

    // ── Range statistics ──

    public HistoricalRangeService.RangeStats rangeStats(String symbol, long fromMs, long toMs) {
        long tickCount = 0L, candleCount = 0L, orderCount = 0L, fillCount = 0L, fillEventCount = 0L;
        long firstTickMs = 0L, lastTickMs = 0L, firstCandleMs = 0L, lastCandleMs = 0L;

        try (PreparedStatement ps = connection.prepareStatement("""
                select count(*) as cnt, min(exchange_timestamp_ms) as first_ts,
                       max(exchange_timestamp_ms) as last_ts
                from feature_ticks where symbol = ? and exchange_timestamp_ms >= ? and exchange_timestamp_ms < ?
                """)) {
            ps.setString(1, symbol); ps.setLong(2, fromMs); ps.setLong(3, toMs);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { tickCount = rs.getLong("cnt"); firstTickMs = rs.getLong("first_ts"); lastTickMs = rs.getLong("last_ts"); }
            }
        } catch (SQLException e) { log.debug("Failed to count ticks: {}", e.getMessage()); }

        try (PreparedStatement ps = connection.prepareStatement("""
                select count(*) as cnt, min(start_time_ms) as first_ts, max(start_time_ms) as last_ts
                from feature_candles where symbol = ? and closed = true and start_time_ms >= ? and start_time_ms < ?
                """)) {
            ps.setString(1, symbol); ps.setLong(2, fromMs); ps.setLong(3, toMs);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { candleCount = rs.getLong("cnt"); firstCandleMs = rs.getLong("first_ts"); lastCandleMs = rs.getLong("last_ts"); }
            }
        } catch (SQLException e) { log.debug("Failed to count candles: {}", e.getMessage()); }

        try (PreparedStatement ps = connection.prepareStatement("""
                select count(*) as cnt from orders where symbol = ?
                and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?)
                and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """)) {
            ps.setString(1, symbol); ps.setLong(2, fromMs); ps.setLong(3, toMs);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) orderCount = rs.getLong("cnt"); }
        } catch (SQLException e) { log.debug("Failed to count orders: {}", e.getMessage()); }

        try (PreparedStatement ps = connection.prepareStatement("""
                select count(*) as cnt from fills where symbol = ?
                and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?)
                and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """)) {
            ps.setString(1, symbol); ps.setLong(2, fromMs); ps.setLong(3, toMs);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) fillCount = rs.getLong("cnt"); }
        } catch (SQLException e) { log.debug("Failed to count fills: {}", e.getMessage()); }

        try (PreparedStatement ps = connection.prepareStatement("""
                select count(*) as cnt from fill_events where symbol = ?
                and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?)
                and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """)) {
            ps.setString(1, symbol); ps.setLong(2, fromMs); ps.setLong(3, toMs);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) fillEventCount = rs.getLong("cnt"); }
        } catch (SQLException e) { log.debug("Failed to count fill events: {}", e.getMessage()); }

        return new HistoricalRangeService.RangeStats(
                symbol, fromMs, toMs, tickCount, candleCount, orderCount, fillCount, fillEventCount,
                tickCount > 0 ? firstTickMs : 0L, tickCount > 0 ? lastTickMs : 0L,
                candleCount > 0 ? firstCandleMs : 0L, candleCount > 0 ? lastCandleMs : 0L);
    }

    Connection connection() {
        return connection;
    }
}
