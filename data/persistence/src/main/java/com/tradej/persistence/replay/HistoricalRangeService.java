package com.tradej.persistence.replay;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFullyFilled;
import com.tradej.core.domain.event.OrderPartiallyFilled;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


/**
 * Reconstructs historical domain event streams from DuckDB for backtesting.
 *
 * <p>Queries the same DuckDB database used by {@link com.tradej.feature.store.DuckDbFeatureStore}
 * and {@link com.tradej.persistence.duckdb.DuckDbEventStore} to retrieve stored ticks, candles,
 * and order events by time range and symbol. Supports both direct data queries and full event-bus
 * replay for backtesting simulations.
 */
public final class HistoricalRangeService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HistoricalRangeService.class);

    private final Connection connection;

    public HistoricalRangeService(Path databasePath) {
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:" + databasePath.toAbsolutePath());
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to connect to DuckDB at " + databasePath, e);
        }
    }

    // ── Candle queries ──

    /**
     * Query completed candles from the feature store within a time range.
     *
     * @param symbol   trading symbol (e.g. "SBIN")
     * @param interval candle interval (e.g. "5m", "1s")
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @param limit    maximum results (default 1000)
     * @return list of completed candles in chronological order
     */
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
            log.warn("Failed to query candles for {} {} [{},{}]: {}", symbol, interval, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    /**
     * Short-hand with default limit of 1000.
     */
    public List<Candle> queryCandles(String symbol, String interval, long fromMs, long toMs) {
        return queryCandles(symbol, interval, fromMs, toMs, 1000);
    }

    // ── Tick queries ──

    /**
     * Query historical ticks from the feature store within a time range.
     *
     * @param symbol trading symbol (e.g. "SBIN")
     * @param fromMs start of range (inclusive, epoch millis)
     * @param toMs   end of range (exclusive, epoch millis)
     * @param limit  maximum results (default 5000)
     * @return list of reconstructed {@link TickReceived} events in chronological order
     */
    public List<TickReceived> queryTicks(String symbol, long fromMs, long toMs, int limit) {
        try (PreparedStatement ps = connection.prepareStatement("""
                select event_id, interval, ltp_paisa, last_trade_quantity,
                       cumulative_volume, exchange_timestamp_ms
                from feature_ticks
                where symbol = ? and exchange_timestamp_ms >= ? and exchange_timestamp_ms < ?
                order by exchange_timestamp_ms asc
                limit ?
                """)) {
            ps.setString(1, symbol);
            ps.setLong(2, fromMs);
            ps.setLong(3, toMs);
            ps.setInt(4, Math.min(limit, 50_000));
            List<TickReceived> ticks = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long exchangeTs = rs.getLong("exchange_timestamp_ms");
                    ticks.add(new TickReceived(
                            EventMetadata.correlated("", 0L),
                            symbol,
                            rs.getString("interval"),
                            rs.getLong("ltp_paisa"),
                            rs.getLong("last_trade_quantity"),
                            rs.getLong("cumulative_volume"),
                            exchangeTs,
                            null // market depth not stored
                    ));
                }
            }
            return ticks;
        } catch (SQLException e) {
            log.warn("Failed to query ticks for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    /**
     * Short-hand with default limit of 5000.
     */
    public List<TickReceived> queryTicks(String symbol, long fromMs, long toMs) {
        return queryTicks(symbol, fromMs, toMs, 5000);
    }

    // ── Order queries ──

    /**
     * Query historical orders from the event store within a time range.
     *
     * <p>Filters by {@code ingested_at_ms} — the wall-clock timestamp when the order
     * was persisted (not the exchange timestamp, which is not stored in the orders table).
     *
     * @param symbol trading symbol (optional — pass null or empty to return all symbols)
     * @param fromMs start of range (inclusive, epoch millis) — filters by ingestion time
     * @param toMs   end of range (exclusive, epoch millis) — filters by ingestion time
     * @param limit  maximum results (default 500)
     * @return list of stored order records keyed by event_id, newest first
     */
    public List<HistoricalOrder> queryOrders(String symbol, long fromMs, long toMs, int limit) {
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
            if (symbol != null && !symbol.isBlank()) {
                ps.setString(idx++, symbol);
            }
            ps.setInt(idx, Math.min(limit, 5000));
            List<HistoricalOrder> orders = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(new HistoricalOrder(
                            rs.getString("event_id"),
                            rs.getString("order_id"),
                            rs.getString("correlation_id"),
                            rs.getString("symbol"),
                            rs.getString("status"),
                            rs.getLong("quantity"),
                            rs.getLong("price_paisa")
                    ));
                }
            }
            return orders;
        } catch (SQLException e) {
            log.warn("Failed to query orders for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    /**
     * Short-hand with default limit of 500.
     */
    public List<HistoricalOrder> queryOrders(String symbol, long fromMs, long toMs) {
        return queryOrders(symbol, fromMs, toMs, 500);
    }

    // ── Fill queries ──

    /**
     * Query historical fills from the event store within a time range.
     *
     * <p>Filters by {@code ingested_at_ms} — the wall-clock timestamp when the fill
     * was persisted (not the exchange timestamp, which is not stored in the fills table).
     *
     * @param symbol trading symbol (optional — pass null or empty to return all symbols)
     * @param fromMs start of range (inclusive, epoch millis) — filters by ingestion time
     * @param toMs   end of range (exclusive, epoch millis) — filters by ingestion time
     * @param limit  maximum results (default 500)
     * @return list of stored fill records, newest first
     */
    public List<HistoricalFill> queryFills(String symbol, long fromMs, long toMs, int limit) {
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
            if (symbol != null && !symbol.isBlank()) {
                ps.setString(idx++, symbol);
            }
            ps.setInt(idx, Math.min(limit, 5000));
            List<HistoricalFill> fills = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fills.add(new HistoricalFill(
                            rs.getString("event_id"),
                            rs.getString("order_id"),
                            rs.getString("trade_id"),
                            rs.getString("symbol"),
                            rs.getLong("quantity"),
                            rs.getLong("price_paisa")
                    ));
                }
            }
            return fills;
        } catch (SQLException e) {
            log.warn("Failed to query fills for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    /**
     * Short-hand with default limit of 500.
     */
    public List<HistoricalFill> queryFills(String symbol, long fromMs, long toMs) {
        return queryFills(symbol, fromMs, toMs, 500);
    }

    // ── Fill-event queries ──

    /**
     * Query historical fill events (PARTIALLY_FILLED / FULLY_FILLED) from the event store
     * within a time range.
     *
     * <p>Filters by {@code ingested_at_ms} — the wall-clock timestamp when the fill event
     * was persisted (not the exchange timestamp, which is not stored in the fill_events table).
     *
     * @param symbol trading symbol (optional — pass null or empty to return all symbols)
     * @param fromMs start of range (inclusive, epoch millis) — filters by ingestion time
     * @param toMs   end of range (exclusive, epoch millis) — filters by ingestion time
     * @param limit  maximum results (default 500)
     * @return list of stored fill event records, newest first
     */
    public List<HistoricalFillEvent> queryFillEvents(String symbol, long fromMs, long toMs, int limit) {
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
            if (symbol != null && !symbol.isBlank()) {
                ps.setString(idx++, symbol);
            }
            ps.setInt(idx, Math.min(limit, 5000));
            List<HistoricalFillEvent> events = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new HistoricalFillEvent(
                            rs.getString("event_id"),
                            rs.getString("event_type"),
                            rs.getString("order_id"),
                            rs.getString("correlation_id"),
                            rs.getString("symbol"),
                            rs.getLong("quantity"),
                            rs.getLong("price_paisa"),
                            rs.getInt("fill_count")
                    ));
                }
            }
            return events;
        } catch (SQLException e) {
            log.warn("Failed to query fill events for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    /**
     * Short-hand with default limit of 500.
     */
    public List<HistoricalFillEvent> queryFillEvents(String symbol, long fromMs, long toMs) {
        return queryFillEvents(symbol, fromMs, toMs, 500);
    }

    // ── Trade-lifecycle queries ──

    /**
     * Query historical trade lifecycle events from the DuckDB event store within a time range.
     *
     * <p>Returns both TRADE_OPENED and TRADE_CLOSED events from the {@code trade_lifecycle}
     * table, ordered by ingestion time ascending (chronological replay order).
     *
     * @param symbol trading symbol (optional — pass null or empty to return all symbols)
     * @param fromMs start of range (inclusive, epoch millis) — filters by ingested_at_ms
     * @param toMs   end of range (exclusive, epoch millis) — filters by ingested_at_ms
     * @param limit  maximum results (default 5000)
     * @return list of historical trade lifecycle records in chronological order
     */
    public List<HistoricalTradeEvent> queryTradeLifecycle(String symbol, long fromMs, long toMs, int limit) {
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
            if (symbol != null && !symbol.isBlank()) {
                ps.setString(idx++, symbol);
            }
            ps.setInt(idx, Math.min(limit, 50_000));
            List<HistoricalTradeEvent> events = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new HistoricalTradeEvent(
                            rs.getString("event_id"),
                            rs.getString("event_type"),
                            rs.getString("trade_id"),
                            rs.getString("order_id"),
                            rs.getString("signal_id"),
                            rs.getString("symbol"),
                            rs.getString("side"),
                            rs.getLong("size"),
                            rs.getLong("entry_price_paisa"),
                            rs.getLong("stop_loss_paisa"),
                            rs.getLong("take_profit_paisa"),
                            rs.getLong("exit_price_paisa"),
                            rs.getLong("realized_pnl_paisa"),
                            rs.getString("close_reason")
                    ));
                }
            }
            return events;
        } catch (SQLException e) {
            log.warn("Failed to query trade lifecycle for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return List.of();
        }
    }

    /**
     * Short-hand with default limit of 5000 and no symbol filter.
     */
    public List<HistoricalTradeEvent> queryTradeLifecycle(long fromMs, long toMs) {
        return queryTradeLifecycle(null, fromMs, toMs, 5000);
    }

    // ── Trade lifecycle replay ──

    /**
     * Replay historical trade lifecycle events through the event bus to rebuild
     * portfolio, risk, and position state at startup.
     *
     * <p>Queries all TRADE_OPENED and TRADE_CLOSED events from the {@code trade_lifecycle}
     * table and publishes them through the event bus in chronological order. Subscribers
     * ({@link com.tradej.execution.position.EventSourcedNetPositionProvider},
     * {@link com.tradej.strategy.portfolio.PortfolioEngine},
     * {@link com.tradej.execution.risk.PositionRiskHandler},
     * {@link com.tradej.app.readmodel.ReadModelStore}) will rebuild their state from
     * the replayed events.
     *
     * @param symbol   trading symbol (optional — pass null or empty to replay all symbols)
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @param eventBus the running event bus to publish events into
     * @return replay summary with total/replayed/failed counts
     */
    public ReplayResult replayTradeLifecycle(String symbol, long fromMs, long toMs, EventBus eventBus) {
        List<HistoricalTradeEvent> events = queryTradeLifecycle(symbol, fromMs, toMs, 50_000);
        long replayed = 0L;
        long failed = 0L;
        for (HistoricalTradeEvent evt : events) {
            try {
                DomainEvent domainEvent = switch (evt.eventType()) {
                    case "TRADE_OPENED" -> {
                        Side side;
                        try {
                            side = Side.valueOf(evt.side());
                        } catch (IllegalArgumentException e) {
                            side = Side.UNKNOWN;
                        }
                        yield new TradeOpened(
                                EventMetadata.correlated(evt.eventId(), 0L),
                                evt.tradeId(),
                                evt.orderId(),
                                evt.signalId(),
                                evt.symbol(),
                                side,
                                evt.size(),
                                evt.entryPricePaisa(),
                                evt.stopLossPaisa(),
                                evt.takeProfitPaisa()
                        );
                    }
                    case "TRADE_CLOSED" -> new TradeClosed(
                            EventMetadata.correlated(evt.eventId(), 0L),
                            evt.tradeId(),
                            evt.symbol(),
                            evt.exitPricePaisa(),
                            evt.realizedPnlPaisa(),
 evt.size(),
                            evt.closeReason()
                    );
                    default -> null;
                };
                if (domainEvent != null) {
                    eventBus.publish(domainEvent);
                    replayed++;
                }
            } catch (Exception e) {
                failed++;
                log.debug("Failed to replay trade lifecycle event {}: {}", evt.eventId(), e.getMessage());
            }
        }
        long total = replayed + failed;
        log.info("Replay trade lifecycle complete for {} [{},{}]: {}/{} replayed, {} failed",
                symbol, fromMs, toMs, replayed, total, failed);
        return new ReplayResult(total, replayed, failed);
    }

    /**
     * Short-hand to replay all trade lifecycle events (no symbol filter, full range).
     */
    public ReplayResult replayTradeLifecycle(EventBus eventBus) {
        return replayTradeLifecycle(null, 0L, Long.MAX_VALUE, eventBus);
    }

    // ── Event replay ──

    /** Replay historical ticks as {@link MarketTickEvent} instances through the event bus
     * to drive the full pipeline. Ticks are published in chronological order, triggering
     * candle aggregation, strategy execution, and signal/order generation as if they
     * arrived live. Uses a synthetic {@link FeedMode#TICKER} and {@link ExchangeSegment#NSE}
     * as defaults since these are not persisted in the ticks table.
     *
     * @param symbol   trading symbol (e.g. "SBIN")
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @param eventBus the running event bus to publish events into
     * @return replay summary with total/replayed/failed counts
     */
    public ReplayResult replayMarketTicks(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return replayMarketTicks(symbol, fromMs, toMs, eventBus, 0, 50_000);
    }

    public ReplayResult replayMarketTicks(
            String symbol,
            long fromMs,
            long toMs,
            EventBus eventBus,
            int offset,
            int batchSize
    ) {
        int limit = Math.min(Math.max(batchSize, 1), 50_000);
        try (PreparedStatement ps = connection.prepareStatement("""
                select event_id, interval, ltp_paisa, last_trade_quantity,
                       cumulative_volume, exchange_timestamp_ms, exchange_segment
                from feature_ticks
                where symbol = ? and exchange_timestamp_ms >= ? and exchange_timestamp_ms < ?
                order by exchange_timestamp_ms asc
                limit ? offset ?
                """)) {
            ps.setString(1, symbol);
            ps.setLong(2, fromMs);
            ps.setLong(3, toMs);
            ps.setInt(4, limit);
            ps.setInt(5, Math.max(0, offset));
            long replayed = 0L;
            long failed = 0L;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        long exchangeTs = rs.getLong("exchange_timestamp_ms");
                        ExchangeSegment segment = resolveExchangeSegment(rs.getString("exchange_segment"));
                        var tick = new MarketTickEvent(
                                EventMetadata.correlated("", 0L),
                                0L,
                                symbol,
                                segment,
                                FeedMode.TICKER,
                                rs.getLong("ltp_paisa"),
                                rs.getLong("last_trade_quantity"),
                                rs.getLong("cumulative_volume"),
                                exchangeTs,
                                java.util.Optional.empty(),
                                0L,
                                0L);
                        eventBus.publish(tick);
                        replayed++;
                    } catch (Exception e) {
                        failed++;
                        log.debug("Failed to replay market tick for {}: {}", symbol, e.getMessage());
                    }
                }
            }
            long total = replayed + failed;
            log.info("Replay market ticks complete for {} [{},{}] offset={} limit={}: {}/{} replayed, {} failed",
                    symbol, fromMs, toMs, offset, limit, replayed, total, failed);
            return new ReplayResult(total, replayed, failed);
        } catch (SQLException e) {
            log.warn("Failed to replay market ticks for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return new ReplayResult(0L, 0L, 0L);
        }
    }

    private static ExchangeSegment resolveExchangeSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return ExchangeSegment.NSE_EQ;
        }
        try {
            return ExchangeSegment.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExchangeSegment.NSE_EQ;
        }
    }

    /**
     * Replay historical ticks through the event bus to drive the full pipeline.
     * Ticks are published in chronological order, triggering candle aggregation,
     * strategy execution, and signal/order generation as if they arrived live.
     *
     * @param symbol   trading symbol (e.g. "SBIN")
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @param eventBus the running event bus to publish events into
     * @return replay summary with total/replayed/failed counts
     */
    public ReplayResult replayTicks(String symbol, long fromMs, long toMs, EventBus eventBus) {
        List<TickReceived> ticks = queryTicks(symbol, fromMs, toMs, 50_000);
        long replayed = 0L;
        long failed = 0L;
        for (TickReceived tick : ticks) {
            try {
                eventBus.publish(tick);
                replayed++;
            } catch (Exception e) {
                failed++;
                log.debug("Failed to replay tick for {} at {}: {}", symbol, tick.exchangeTimestampMs(), e.getMessage());
            }
        }
        long total = ticks.size();
        log.info("Replay ticks complete for {} [{},{}]: {}/{} replayed, {} failed",
                symbol, fromMs, toMs, replayed, total, failed);
        return new ReplayResult(total, replayed, failed);
    }

    /**
     * Replay historical candle-closed events through the event bus to drive
     * strategy execution directly (skips the tick→candle aggregation step).
     *
     * @param symbol   trading symbol (e.g. "SBIN")
     * @param interval candle interval (e.g. "5m")
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @param eventBus the running event bus to publish events into
     * @return replay summary with total/replayed/failed counts
     */
    public ReplayResult replayCandles(String symbol, String interval, long fromMs, long toMs, EventBus eventBus) {
        List<Candle> candles = queryCandles(symbol, interval, fromMs, toMs, 10_000);
        long replayed = 0L;
        long failed = 0L;
        for (Candle candle : candles) {
            try {
                var closed = new CandleClosed(
                        EventMetadata.correlated("", 0L),
                        candle
                );
                eventBus.publish(closed);
                replayed++;
            } catch (Exception e) {
                failed++;
                log.debug("Failed to replay candle for {} {} at {}: {}", symbol, interval, candle.startTimeMs(), e.getMessage());
            }
        }
        long total = candles.size();
        log.info("Replay candles complete for {} {} [{},{}]: {}/{} replayed, {} failed",
                symbol, interval, fromMs, toMs, replayed, total, failed);
        return new ReplayResult(total, replayed, failed);
    }

    /**
     * Replay historical fill events (PARTIALLY_FILLED / FULLY_FILLED) through the event bus
     * to drive order lifecycle tracking, position risk, and execution reconciliation.
     *
     * <p>Reconstructs {@link OrderPartiallyFilled} and {@link OrderFullyFilled} domain events
     * from the {@code fill_events} table, publishing them in chronological ingestion order.
     * Metadata fields (exchange segment, side, product type, order type) use defaults since
     * they are not persisted in the fill_events table.
     *
     * @param symbol   trading symbol (optional — pass null or empty to replay all symbols)
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @param eventBus the running event bus to publish events into
     * @return replay summary with total/replayed/failed counts
     */
    /**
     * Replay historical orders through the event bus to drive execution tracking
     * and order lifecycle management.
     *
     * <p>Reconstructs {@link OrderAccepted} domain events from the {@code orders} table,
     * publishing them in chronological ingestion order. Metadata fields (exchange segment,
     * side, product type, order type) use defaults since they are not persisted in the
     * orders table.
     *
     * @param symbol   trading symbol (optional — pass null or empty to replay all symbols)
     * @param fromMs   start of range (inclusive, epoch millis)
     * @param toMs     end of range (exclusive, epoch millis)
     * @param eventBus the running event bus to publish events into
     * @return replay summary with total/replayed/failed counts
     */
    public ReplayResult replayOrders(String symbol, long fromMs, long toMs, EventBus eventBus) {
        String sql = """
                select event_id, order_id, correlation_id, symbol, status, quantity, price_paisa
                from orders
                where (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?) and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """ + (symbol != null && !symbol.isBlank() ? " and symbol = ?" : "") + """
                order by coalesce(event_time_ms, ingested_at_ms) asc nulls last, event_id asc
                limit 10000
                """;
        long replayed = 0L;
        long failed = 0L;
        int rowCount = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, fromMs);
            ps.setLong(idx++, toMs);
            if (symbol != null && !symbol.isBlank()) {
                ps.setString(idx, symbol);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rowCount++;
                    try {
                        String orderId = rs.getString("order_id");
                        String correlationId = rs.getString("correlation_id");
                        String sym = rs.getString("symbol");
                        String statusStr = rs.getString("status");
                        long qty = rs.getLong("quantity");
                        long price = rs.getLong("price_paisa");

                        OrderStatus orderStatus;
                        try {
                            orderStatus = OrderStatus.valueOf(statusStr);
                        } catch (IllegalArgumentException e) {
                            orderStatus = OrderStatus.UNKNOWN;
                        }

                        Order order = new Order(
                                orderId,
                                correlationId,
                                sym,
                                ExchangeSegment.UNKNOWN,
                                Side.UNKNOWN,
                                ProductType.INTRADAY,
                                OrderType.MARKET,
                                orderStatus,
                                qty,
                                0L,  // filledQuantity — unknown for accepted orders
                                price,
                                0L,  // triggerPricePaisa
                                0L,  // exchangeTimeMs
                                ""   // rejectionReason
                        );

                        var event = new OrderAccepted(EventMetadata.correlated(correlationId, 0L), order);
                        eventBus.publish(event);
                        replayed++;
                    } catch (Exception e) {
                        failed++;
                        log.debug("Failed to replay order {}: {}", rs.getString("event_id"), e.getMessage());
                    }
                }
            }
            if (rowCount >= 10000) {
                log.warn("Replay hit the 10,000 row limit for orders in range [{}, {}] — data may be truncated",
                        symbol, fromMs, toMs);
            }
        } catch (SQLException e) {
            log.warn("Failed to replay orders for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
        }
        long total = replayed + failed;
        log.info("Replay orders complete for {} [{},{}]: {}/{} replayed, {} failed",
                symbol, fromMs, toMs, replayed, total, failed);
        return new ReplayResult(total, replayed, failed);
    }

    public ReplayResult replayFillEvents(String symbol, long fromMs, long toMs, EventBus eventBus) {
        String sql = """
                select event_id, event_type, order_id, correlation_id, symbol,
                       quantity, price_paisa, fill_count,
                       coalesce(total_quantity, 0) as total_qty,
                       coalesce(filled_quantity, 0) as filled_qty,
                       coalesce(exchange_segment, '') as exch_seg,
                       coalesce(side, '') as side_str
                from fill_events
                where (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?) and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """ + (symbol != null && !symbol.isBlank() ? " and symbol = ?" : "") + """
                order by coalesce(event_time_ms, ingested_at_ms) asc nulls last, event_id asc
                limit 10000
                """;
        long replayed = 0L;
        long failed = 0L;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, fromMs);
            ps.setLong(idx++, toMs);
            if (symbol != null && !symbol.isBlank()) {
                ps.setString(idx, symbol);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        String eventType = rs.getString("event_type");
                        String eventId = rs.getString("event_id");
                        String orderId = rs.getString("order_id");
                        String correlationId = rs.getString("correlation_id");
                        String sym = rs.getString("symbol");
                        long qty = rs.getLong("quantity");
                        long price = rs.getLong("price_paisa");
                        int fillCount = rs.getInt("fill_count");
                        long totalQty = rs.getLong("total_qty");
                        long filledQty = rs.getLong("filled_qty");
                        String exchSegStr = rs.getString("exch_seg");
                        String sideStr = rs.getString("side_str");

                        ExchangeSegment exchSeg = ExchangeSegment.UNKNOWN;
                        if (exchSegStr != null && !exchSegStr.isBlank()) {
                            try { exchSeg = ExchangeSegment.valueOf(exchSegStr); }
                            catch (IllegalArgumentException ignored) {}
                        }
                        Side side = Side.UNKNOWN;
                        if (sideStr != null && !sideStr.isBlank()) {
                            try { side = Side.valueOf(sideStr); }
                            catch (IllegalArgumentException ignored) {}
                        }
                        OrderStatus orderStatus = "PARTIALLY_FILLED".equals(eventType)
                                ? OrderStatus.PART_TRADED : OrderStatus.TRADED;

                        // Use stored total/filled quantities when available, fall back
                        // to legacy behavior for pre-migration data
                        long useTotalQty = totalQty > 0 ? totalQty : qty;
                        long useFilledQty = filledQty > 0 ? filledQty : qty;

                        // Legacy rows lack filled_quantity — estimate when still missing after fallback
                        if ("PARTIALLY_FILLED".equals(eventType) && filledQty == 0L && useFilledQty >= useTotalQty) {
                            useFilledQty = useTotalQty > qty ? useTotalQty - qty : qty / 2;
                        }

                        Order order = new Order(
                                orderId, correlationId, sym, exchSeg, side,
                                ProductType.INTRADAY, OrderType.MARKET, orderStatus,
                                useTotalQty, useFilledQty, price, 0L, 0L, "");

                        List<Trade> trades = new ArrayList<>();
                        long perFillQty = fillCount > 0 ? qty / fillCount : qty;
                        long remainder = fillCount > 0 ? qty % fillCount : 0L;
                        for (int i = 0; i < fillCount; i++) {
                            long fillQty = perFillQty + (i == 0 ? remainder : 0L);
                            if (fillQty <= 0L) {
                                continue;
                            }
                            trades.add(new Trade(eventId + "-" + i, orderId, sym,
                                    exchSeg, side, fillQty, price, 0L));
                        }
                        if (trades.isEmpty()) {
                            trades.add(new Trade(eventId + "-0", orderId, sym,
                                    exchSeg, side, qty, price, 0L));
                        }

                        DomainEvent event = "PARTIALLY_FILLED".equals(eventType)
                                ? new OrderPartiallyFilled(
                                        EventMetadata.correlated(correlationId, 0L), order, trades)
                                : new OrderFullyFilled(
                                        EventMetadata.correlated(correlationId, 0L), order, trades);

                        eventBus.publish(event);
                        replayed++;
                    } catch (Exception e) {
                        failed++;
                        log.debug("Failed to replay fill event {}: {}", rs.getString("event_id"), e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to replay fill events for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
        }
        long total = replayed + failed;
        log.info("Replay fill events complete for {} [{},{}]: {}/{} replayed, {} failed",
                symbol, fromMs, toMs, replayed, total, failed);
        return new ReplayResult(total, replayed, failed);
    }

    // ── Range statistics ──

    /**
     * Statistics about historical data availability for a symbol/range.
     */
    public RangeStats rangeStats(String symbol, long fromMs, long toMs) {
        long tickCount = 0L;
        long candleCount = 0L;
        long orderCount = 0L;
        long fillCount = 0L;
        long fillEventCount = 0L;
        long firstTickMs = 0L;
        long lastTickMs = 0L;
        long firstCandleMs = 0L;
        long lastCandleMs = 0L;

        try (PreparedStatement tickPs = connection.prepareStatement("""
                select count(*) as cnt, min(exchange_timestamp_ms) as first_ts,
                       max(exchange_timestamp_ms) as last_ts
                from feature_ticks
                where symbol = ? and exchange_timestamp_ms >= ? and exchange_timestamp_ms < ?
                """)) {
            tickPs.setString(1, symbol);
            tickPs.setLong(2, fromMs);
            tickPs.setLong(3, toMs);
            try (ResultSet rs = tickPs.executeQuery()) {
                if (rs.next()) {
                    tickCount = rs.getLong("cnt");
                    firstTickMs = rs.getLong("first_ts");
                    lastTickMs = rs.getLong("last_ts");
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to count ticks for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
        }

        try (PreparedStatement candlePs = connection.prepareStatement("""
                select count(*) as cnt, min(start_time_ms) as first_ts,
                       max(start_time_ms) as last_ts
                from feature_candles
                where symbol = ? and closed = true
                  and start_time_ms >= ? and start_time_ms < ?
                """)) {
            candlePs.setString(1, symbol);
            candlePs.setLong(2, fromMs);
            candlePs.setLong(3, toMs);
            try (ResultSet rs = candlePs.executeQuery()) {
                if (rs.next()) {
                    candleCount = rs.getLong("cnt");
                    firstCandleMs = rs.getLong("first_ts");
                    lastCandleMs = rs.getLong("last_ts");
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to count candles for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
        }

        try (PreparedStatement orderPs = connection.prepareStatement("""
                select count(*) as cnt
                from orders
                where symbol = ? and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?) and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """)) {
            orderPs.setString(1, symbol);
            orderPs.setLong(2, fromMs);
            orderPs.setLong(3, toMs);
            try (ResultSet rs = orderPs.executeQuery()) {
                if (rs.next()) {
                    orderCount = rs.getLong("cnt");
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to count orders for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
        }

        try (PreparedStatement fillPs = connection.prepareStatement("""
                select count(*) as cnt
                from fills
                where symbol = ? and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?) and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """)) {
            fillPs.setString(1, symbol);
            fillPs.setLong(2, fromMs);
            fillPs.setLong(3, toMs);
            try (ResultSet rs = fillPs.executeQuery()) {
                if (rs.next()) {
                    fillCount = rs.getLong("cnt");
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to count fills for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
        }

        try (PreparedStatement fillEventPs = connection.prepareStatement("""
                select count(*) as cnt
                from fill_events
                where symbol = ? and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) >= ?) and (coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(event_time_ms, ingested_at_ms) < ?)
                """)) {
            fillEventPs.setString(1, symbol);
            fillEventPs.setLong(2, fromMs);
            fillEventPs.setLong(3, toMs);
            try (ResultSet rs = fillEventPs.executeQuery()) {
                if (rs.next()) {
                    fillEventCount = rs.getLong("cnt");
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to count fill events for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
        }

        return new RangeStats(
                symbol, fromMs, toMs, tickCount, candleCount, orderCount,
                fillCount, fillEventCount,
                tickCount > 0 ? firstTickMs : 0L,
                tickCount > 0 ? lastTickMs : 0L,
                candleCount > 0 ? firstCandleMs : 0L,
                candleCount > 0 ? lastCandleMs : 0L
        );
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }

    // ── Result types ──

    /**
     * Lightweight order record returned by historical queries.
     */
    public record HistoricalOrder(
            String eventId,
            String orderId,
            String correlationId,
            String symbol,
            String status,
            long quantity,
            long pricePaisa
    ) {
    }

    /**
     * Lightweight fill record returned by historical queries.
     */
    public record HistoricalFill(
            String eventId,
            String orderId,
            String tradeId,
            String symbol,
            long quantity,
            long pricePaisa
    ) {
    }

    /**
     * Lightweight fill-event record returned by historical queries.
     */
    public record HistoricalFillEvent(
            String eventId,
            String eventType,
            String orderId,
            String correlationId,
            String symbol,
            long quantity,
            long pricePaisa,
            int fillCount
    ) {
    }

    /**
     * Statistics about historical data availability for a time range.
     */
    /**
     * Lightweight trade lifecycle event record returned by historical queries.
     */
    public record HistoricalTradeEvent(
            String eventId,
            String eventType,
            String tradeId,
            String orderId,
            String signalId,
            String symbol,
            String side,
            long size,
            long entryPricePaisa,
            long stopLossPaisa,
            long takeProfitPaisa,
            long exitPricePaisa,
            long realizedPnlPaisa,
            String closeReason
    ) {
    }

    public record RangeStats(
            String symbol,
            long fromMs,
            long toMs,
            long tickCount,
            long candleCount,
            long orderCount,
            long fillCount,
            long fillEventCount,
            long firstTickMs,
            long lastTickMs,
            long firstCandleMs,
            long lastCandleMs
    ) {
        /** True if any data exists for this range across all tables. */
        public boolean hasData() {
            return tickCount > 0L || candleCount > 0L || orderCount > 0L
                    || fillCount > 0L || fillEventCount > 0L;
        }
    }
}
