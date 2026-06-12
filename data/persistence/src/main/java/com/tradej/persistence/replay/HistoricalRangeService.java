package com.tradej.persistence.replay;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFullyFilled;
import com.tradej.core.domain.event.OrderPartiallyFilled;
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
import com.tradej.persistence.duckdb.DuckDbConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * Facade composing historical data queries and event replay for DuckDB.
 *
 * <p>Delegates to:
 * <ul>
 *   <li>{@link HistoricalQueryService} — read-only SQL queries (candles, ticks, orders, fills, stats)</li>
 *   <li>{@link HistoricalEventReplayService} — event reconstruction and EventBus publishing</li>
 * </ul>
 *
 * <p>All public methods preserve backward compatibility with the pre-decomposition API.
 */
public final class HistoricalRangeService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HistoricalRangeService.class);

    private final DuckDbConnectionPool pool;
    private final Connection connection;
    private final HistoricalQueryService queryService;
    private final HistoricalEventReplayService replayService;

    public HistoricalRangeService(Path databasePath) {
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:" + databasePath.toAbsolutePath());
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to connect to DuckDB at " + databasePath, e);
        }
        this.pool = null;
        this.queryService = new HistoricalQueryService(connection);
        this.replayService = new HistoricalEventReplayService(connection);
    }

    public HistoricalRangeService(DuckDbConnectionPool pool) {
        this.pool = pool;
        this.connection = pool.rawConnection();
        this.queryService = new HistoricalQueryService(connection);
        this.replayService = new HistoricalEventReplayService(connection);
    }

    private <T> T execute(java.util.function.Supplier<T> action) {
        if (pool != null) {
            return pool.withConnection(conn -> action.get());
        } else {
            return action.get();
        }
    }

    // ── Query delegation ──

    public List<Candle> queryCandles(String symbol, String interval, long fromMs, long toMs, int limit) {
        return execute(() -> queryService.queryCandles(symbol, interval, fromMs, toMs, limit));
    }

    public List<Candle> queryCandles(String symbol, String interval, long fromMs, long toMs) {
        return execute(() -> queryService.queryCandles(symbol, interval, fromMs, toMs));
    }

    public List<MarketTickEvent> queryTicks(String symbol, long fromMs, long toMs, int limit) {
        return execute(() -> queryService.queryTicks(symbol, fromMs, toMs, limit));
    }

    public List<MarketTickEvent> queryTicks(String symbol, long fromMs, long toMs) {
        return execute(() -> queryService.queryTicks(symbol, fromMs, toMs));
    }

    public List<HistoricalOrder> queryOrders(String symbol, long fromMs, long toMs, int limit) {
        return execute(() -> queryService.queryOrders(symbol, fromMs, toMs, limit));
    }

    public List<HistoricalOrder> queryOrders(String symbol, long fromMs, long toMs) {
        return execute(() -> queryService.queryOrders(symbol, fromMs, toMs));
    }

    public List<HistoricalFill> queryFills(String symbol, long fromMs, long toMs, int limit) {
        return execute(() -> queryService.queryFills(symbol, fromMs, toMs, limit));
    }

    public List<HistoricalFill> queryFills(String symbol, long fromMs, long toMs) {
        return execute(() -> queryService.queryFills(symbol, fromMs, toMs));
    }

    public List<HistoricalFillEvent> queryFillEvents(String symbol, long fromMs, long toMs, int limit) {
        return execute(() -> queryService.queryFillEvents(symbol, fromMs, toMs, limit));
    }

    public List<HistoricalFillEvent> queryFillEvents(String symbol, long fromMs, long toMs) {
        return execute(() -> queryService.queryFillEvents(symbol, fromMs, toMs));
    }

    public List<HistoricalTradeEvent> queryTradeLifecycle(String symbol, long fromMs, long toMs, int limit) {
        return execute(() -> queryService.queryTradeLifecycle(symbol, fromMs, toMs, limit));
    }

    public List<HistoricalTradeEvent> queryTradeLifecycle(long fromMs, long toMs) {
        return execute(() -> queryService.queryTradeLifecycle(fromMs, toMs));
    }

    public RangeStats rangeStats(String symbol, long fromMs, long toMs) {
        return execute(() -> queryService.rangeStats(symbol, fromMs, toMs));
    }

    // ── Replay delegation ──

    public ReplayResult replayTradeLifecycle(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return execute(() -> {
            List<HistoricalTradeEvent> events = queryService.queryTradeLifecycle(symbol, fromMs, toMs, 50_000);
            return replayService.replayTradeLifecycle(events, eventBus);
        });
    }

    public ReplayResult replayTradeLifecycle(EventBus eventBus) {
        return replayTradeLifecycle(null, 0L, Long.MAX_VALUE, eventBus);
    }

    public ReplayResult replayMarketTicks(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return execute(() -> replayService.replayMarketTicks(symbol, fromMs, toMs, eventBus, 0, 50_000));
    }

    public ReplayResult replayMarketTicks(String symbol, long fromMs, long toMs, EventBus eventBus, int offset, int batchSize) {
        return execute(() -> replayService.replayMarketTicks(symbol, fromMs, toMs, eventBus, offset, batchSize));
    }

    public ReplayResult replayTicks(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return execute(() -> {
            List<MarketTickEvent> ticks = queryService.queryTicks(symbol, fromMs, toMs, 50_000);
            return replayService.replayTicks(ticks, symbol, eventBus);
        });
    }

    public ReplayResult replayCandles(String symbol, String interval, long fromMs, long toMs, EventBus eventBus) {
        return execute(() -> {
            List<Candle> candles = queryService.queryCandles(symbol, interval, fromMs, toMs, 10_000);
            return replayService.replayCandles(candles, symbol, interval, eventBus);
        });
    }

    public ReplayResult replayOrders(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return execute(() -> replayService.replayOrders(symbol, fromMs, toMs, eventBus));
    }

    public ReplayResult replayFillEvents(String symbol, long fromMs, long toMs, EventBus eventBus) {
        return execute(() -> replayService.replayFillEvents(symbol, fromMs, toMs, eventBus));
    }

    // ── Accessors ──

    public HistoricalQueryService queryService() {
        return queryService;
    }

    public HistoricalEventReplayService replayService() {
        return replayService;
    }

    @Override
    public void close() throws Exception {
        if (pool == null) {
            connection.close();
        }
    }

    // ── Result types ──

    public record HistoricalOrder(
            String eventId, String orderId, String correlationId,
            String symbol, String status, long quantity, long pricePaisa) {}

    public record HistoricalFill(
            String eventId, String orderId, String tradeId,
            String symbol, long quantity, long pricePaisa) {}

    public record HistoricalFillEvent(
            String eventId, String eventType, String orderId, String correlationId,
            String symbol, long quantity, long pricePaisa, int fillCount) {}

    public record HistoricalTradeEvent(
            String eventId, String eventType, String tradeId, String orderId, String signalId,
            String symbol, String side, long size, long entryPricePaisa, long stopLossPaisa,
            long takeProfitPaisa, long exitPricePaisa, long realizedPnlPaisa, String closeReason) {}

    public record RangeStats(
            String symbol, long fromMs, long toMs,
            long tickCount, long candleCount, long orderCount, long fillCount, long fillEventCount,
            long firstTickMs, long lastTickMs, long firstCandleMs, long lastCandleMs) {
        public boolean hasData() {
            return tickCount > 0L || candleCount > 0L || orderCount > 0L
                    || fillCount > 0L || fillEventCount > 0L;
        }
    }
}
