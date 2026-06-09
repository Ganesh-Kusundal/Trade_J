package com.tradej.persistence.replay;

import com.tradej.core.domain.event.*;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays historical data from DuckDB through an {@link EventBus} for backtesting.
 *
 * <p>Separated from {@link HistoricalRangeService} (query-only) to enforce SRP:
 * this class handles event reconstruction and bus publishing, while
 * {@code HistoricalRangeService} handles SQL queries and data retrieval.
 */
public final class HistoricalEventReplayService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalEventReplayService.class);

    private final Connection connection;

    public HistoricalEventReplayService(Connection connection) {
        this.connection = connection;
    }

    public ReplayResult replayTradeLifecycle(
            List<HistoricalRangeService.HistoricalTradeEvent> events,
            EventBus eventBus
    ) {
        long replayed = 0L;
        long failed = 0L;
        for (var evt : events) {
            try {
                DomainEvent domainEvent = switch (evt.eventType()) {
                    case "TRADE_OPENED" -> {
                        Side side;
                        try { side = Side.valueOf(evt.side()); }
                        catch (IllegalArgumentException e) { side = Side.UNKNOWN; }
                        yield new TradeOpened(
                                EventMetadata.correlated(evt.eventId(), 0L),
                                evt.tradeId(), evt.orderId(), evt.signalId(),
                                evt.symbol(), side, evt.size(),
                                evt.entryPricePaisa(), evt.stopLossPaisa(), evt.takeProfitPaisa()
                        );
                    }
                    case "TRADE_CLOSED" -> new TradeClosed(
                            EventMetadata.correlated(evt.eventId(), 0L),
                            evt.tradeId(), evt.symbol(), evt.exitPricePaisa(),
                            evt.realizedPnlPaisa(), evt.size(), evt.closeReason()
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
        log.info("Replay trade lifecycle: {}/{} replayed, {} failed", replayed, total, failed);
        return new ReplayResult(total, replayed, failed);
    }

    public ReplayResult replayTicks(List<MarketTickEvent> ticks, String symbol, EventBus eventBus) {
        long replayed = 0L;
        long failed = 0L;
        for (MarketTickEvent tick : ticks) {
            try {
                eventBus.publish(tick);
                replayed++;
            } catch (Exception e) {
                failed++;
                log.debug("Failed to replay tick for {} at {}: {}", symbol, tick.exchangeTimestampEpochMs(), e.getMessage());
            }
        }
        long total = ticks.size();
        log.info("Replay ticks for {}: {}/{} replayed, {} failed", symbol, replayed, total, failed);
        return new ReplayResult(total, replayed, failed);
    }

    public ReplayResult replayCandles(List<Candle> candles, String symbol, String interval, EventBus eventBus) {
        long replayed = 0L;
        long failed = 0L;
        for (Candle candle : candles) {
            try {
                eventBus.publish(new CandleClosed(EventMetadata.correlated("", 0L), candle));
                replayed++;
            } catch (Exception e) {
                failed++;
                log.debug("Failed to replay candle for {} {} at {}: {}", symbol, interval, candle.startTimeMs(), e.getMessage());
            }
        }
        long total = candles.size();
        log.info("Replay candles for {} {}: {}/{} replayed, {} failed", symbol, interval, replayed, total, failed);
        return new ReplayResult(total, replayed, failed);
    }

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
                        String orderId = rs.getString("order_id");
                        String correlationId = rs.getString("correlation_id");
                        String sym = rs.getString("symbol");
                        OrderStatus orderStatus;
                        try { orderStatus = OrderStatus.valueOf(rs.getString("status")); }
                        catch (IllegalArgumentException e) { orderStatus = OrderStatus.UNKNOWN; }

                        Order order = new Order(orderId, correlationId, sym,
                                ExchangeSegment.UNKNOWN, Side.UNKNOWN, ProductType.INTRADAY, OrderType.MARKET,
                                orderStatus, rs.getLong("quantity"), 0L, rs.getLong("price_paisa"), 0L, 0L, "");

                        eventBus.publish(new OrderAccepted(EventMetadata.correlated(correlationId, 0L), order));
                        replayed++;
                    } catch (Exception e) {
                        failed++;
                        log.debug("Failed to replay order {}: {}", rs.getString("event_id"), e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to replay orders for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
        }
        long total = replayed + failed;
        log.info("Replay orders for {} [{},{}]: {}/{} replayed, {} failed", symbol, fromMs, toMs, replayed, total, failed);
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

                        long useTotalQty = totalQty > 0 ? totalQty : qty;
                        long useFilledQty = filledQty > 0 ? filledQty : qty;
                        if ("PARTIALLY_FILLED".equals(eventType) && filledQty == 0L && useFilledQty >= useTotalQty) {
                            useFilledQty = useTotalQty > qty ? useTotalQty - qty : qty / 2;
                        }

                        Order order = new Order(orderId, correlationId, sym, exchSeg, side,
                                ProductType.INTRADAY, OrderType.MARKET, orderStatus,
                                useTotalQty, useFilledQty, price, 0L, 0L, "");

                        List<Trade> trades = new ArrayList<>();
                        long perFillQty = fillCount > 0 ? qty / fillCount : qty;
                        long remainder = fillCount > 0 ? qty % fillCount : 0L;
                        for (int i = 0; i < fillCount; i++) {
                            long fillQty = perFillQty + (i == 0 ? remainder : 0L);
                            if (fillQty <= 0L) continue;
                            trades.add(new Trade(eventId + "-" + i, orderId, sym, exchSeg, side, fillQty, price, 0L));
                        }
                        if (trades.isEmpty()) {
                            trades.add(new Trade(eventId + "-0", orderId, sym, exchSeg, side, qty, price, 0L));
                        }

                        DomainEvent event = "PARTIALLY_FILLED".equals(eventType)
                                ? new OrderPartiallyFilled(EventMetadata.correlated(correlationId, 0L), order, trades)
                                : new OrderFullyFilled(EventMetadata.correlated(correlationId, 0L), order, trades);

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
        log.info("Replay fill events for {} [{},{}]: {}/{} replayed, {} failed", symbol, fromMs, toMs, replayed, total, failed);
        return new ReplayResult(total, replayed, failed);
    }

    public ReplayResult replayMarketTicks(
            String symbol, long fromMs, long toMs, EventBus eventBus, int offset, int batchSize
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
                        ExchangeSegment segment = resolveExchangeSegment(rs.getString("exchange_segment"));
                        var tick = new MarketTickEvent(
                                EventMetadata.correlated("", 0L), 0L, symbol, segment, FeedMode.TICKER,
                                rs.getLong("ltp_paisa"), rs.getLong("last_trade_quantity"),
                                rs.getLong("cumulative_volume"), rs.getLong("exchange_timestamp_ms"),
                                java.util.Optional.empty(), 0L, 0L);
                        eventBus.publish(tick);
                        replayed++;
                    } catch (Exception e) {
                        failed++;
                        log.debug("Failed to replay market tick for {}: {}", symbol, e.getMessage());
                    }
                }
            }
            long total = replayed + failed;
            log.info("Replay market ticks for {} [{},{}] offset={} limit={}: {}/{} replayed, {} failed",
                    symbol, fromMs, toMs, offset, limit, replayed, total, failed);
            return new ReplayResult(total, replayed, failed);
        } catch (SQLException e) {
            log.warn("Failed to replay market ticks for {} [{},{}]: {}", symbol, fromMs, toMs, e.getMessage());
            return new ReplayResult(0L, 0L, 0L);
        }
    }

    private static ExchangeSegment resolveExchangeSegment(String raw) {
        if (raw == null || raw.isBlank()) return ExchangeSegment.NSE_EQ;
        try { return ExchangeSegment.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return ExchangeSegment.NSE_EQ; }
    }
}
