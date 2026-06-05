package com.tradej.persistence.duckdb;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderFullyFilled;
import com.tradej.core.domain.event.OrderPartiallyFilled;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.port.DomainEventHandler;

import java.util.List;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DuckDbEventStore implements DomainEventHandler<DomainEvent>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DuckDbEventStore.class);

    private final Path databasePath;
    private final LongSupplier ingestedAtMs;
    private Connection connection;

    public DuckDbEventStore(Path databasePath) {
        this(databasePath, System::currentTimeMillis);
    }

    public DuckDbEventStore(Path databasePath, LongSupplier ingestedAtMs) {
        this.databasePath = databasePath;
        this.ingestedAtMs = ingestedAtMs;
        initConnection();
    }

    private void initConnection() {
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:" + databasePath.toAbsolutePath());
            bootstrap();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize DuckDB store at " + databasePath, e);
        }
    }

    /**
     * Validates the connection and reconnects if broken.
     * Synchronized to prevent concurrent reconnect attempts.
     */
    private synchronized void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            log.warn("DuckDB connection lost — reconnecting to {}", databasePath);
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (Exception ignored) {
                // ignore
            }
            initConnection();
            log.info("DuckDB reconnected successfully");
        }
    }

    @Override
    public synchronized void onEvent(DomainEvent event) throws Exception {
        try {
            ensureConnection();
            switch (event) {
                case CandleClosed candleClosed -> insertCandle(candleClosed);
                case OrderAccepted orderAccepted -> insertOrder(orderAccepted);
                case OrderFilled orderFilled -> insertFill(orderFilled);
                case OrderPartiallyFilled partiallyFilled ->
                        insertFillEvent("PARTIALLY_FILLED", partiallyFilled);
                case OrderFullyFilled fullyFilled ->
                        insertFillEvent("FULLY_FILLED", fullyFilled);
                case TradeOpened opened -> insertTradeOpened(opened);
                case TradeClosed closed -> insertTradeClosed(closed);
                default -> { }
            }
        } catch (SQLException e) {
            log.error("Failed to persist event type={} id={}: {}", 
                    event.getClass().getSimpleName(), event.eventId(), e.getMessage());
            throw e; // Let AsyncDispatchHandler handle the retry
        }
    }

    private void bootstrap() throws SQLException {
        connection.createStatement().execute("""
                create table if not exists candles (
                    event_id varchar,
                    symbol varchar,
                    interval varchar,
                    start_time_ms bigint,
                    end_time_ms bigint,
                    open_paisa bigint,
                    high_paisa bigint,
                    low_paisa bigint,
                    close_paisa bigint,
                    volume bigint
                )
                """);
        connection.createStatement().execute("""
                create table if not exists orders (
                    event_id varchar,
                    order_id varchar,
                    correlation_id varchar,
                    symbol varchar,
                    status varchar,
                    quantity bigint,
                    price_paisa bigint,
                    ingested_at_ms bigint,
                    event_time_ms bigint
                )
                """);
        connection.createStatement().execute(
                "alter table orders add column if not exists ingested_at_ms bigint");
        connection.createStatement().execute(
                "alter table orders add column if not exists event_time_ms bigint");
        connection.createStatement().execute("""
                create table if not exists fills (
                    event_id varchar,
                    order_id varchar,
                    trade_id varchar,
                    symbol varchar,
                    quantity bigint,
                    price_paisa bigint,
                    ingested_at_ms bigint,
                    event_time_ms bigint
                )
                """);
        connection.createStatement().execute(
                "alter table fills add column if not exists ingested_at_ms bigint");
        connection.createStatement().execute(
                "alter table fills add column if not exists event_time_ms bigint");
        // Use a single CREATE TABLE with all columns (fixes DF-01).
        // ALTER TABLE migrations for columns added in later versions are kept
        // for backward compatibility with existing databases.
        connection.createStatement().execute("""
                create table if not exists fill_events (
                    event_id varchar,
                    event_type varchar,
                    order_id varchar,
                    correlation_id varchar,
                    symbol varchar,
                    quantity bigint,
                    price_paisa bigint,
                    fill_count integer,
                    total_quantity bigint default 0,
                    filled_quantity bigint default 0,
                    exchange_segment varchar default '',
                    side varchar default '',
                    ingested_at_ms bigint,
                    event_time_ms bigint
                )
                """);
        connection.createStatement().execute(
                "alter table fill_events add column if not exists event_time_ms bigint");
        connection.createStatement().execute("""
                create table if not exists trade_lifecycle (
                    event_id varchar,
                    event_type varchar,
                    trade_id varchar,
                    order_id varchar,
                    signal_id varchar,
                    symbol varchar,
                    side varchar,
                    size bigint,
                    entry_price_paisa bigint,
                    stop_loss_paisa bigint default 0,
                    take_profit_paisa bigint default 0,
                    exit_price_paisa bigint default 0,
                    realized_pnl_paisa bigint default 0,
                    close_reason varchar default '',
                    ingested_at_ms bigint,
                    event_time_ms bigint
                )
                """);
        connection.createStatement().execute(
                "alter table trade_lifecycle add column if not exists event_time_ms bigint");
        backfillEventTimeColumns();
    }

    private void backfillEventTimeColumns() throws SQLException {
        connection.createStatement().execute(
                "update orders set event_time_ms = ingested_at_ms where event_time_ms is null and ingested_at_ms is not null");
        connection.createStatement().execute(
                "update fills set event_time_ms = ingested_at_ms where event_time_ms is null and ingested_at_ms is not null");
        connection.createStatement().execute(
                "update fill_events set event_time_ms = ingested_at_ms where event_time_ms is null and ingested_at_ms is not null");
        connection.createStatement().execute(
                "update trade_lifecycle set event_time_ms = ingested_at_ms where event_time_ms is null and ingested_at_ms is not null");
    }

    private void insertCandle(CandleClosed event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into candles values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, event.eventId());
            statement.setString(2, event.candle().symbol());
            statement.setString(3, event.candle().interval());
            statement.setLong(4, event.candle().startTimeMs());
            statement.setLong(5, event.candle().endTimeMs());
            statement.setLong(6, event.candle().openPaisa());
            statement.setLong(7, event.candle().highPaisa());
            statement.setLong(8, event.candle().lowPaisa());
            statement.setLong(9, event.candle().closePaisa());
            statement.setLong(10, event.candle().volume());
            statement.executeUpdate();
        }
    }

    private void insertOrder(OrderAccepted event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into orders values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, event.eventId());
            statement.setString(2, event.order().orderId());
            statement.setString(3, event.order().correlationId());
            statement.setString(4, event.order().symbol());
            statement.setString(5, event.order().status().name());
            statement.setLong(6, event.order().quantity());
            statement.setLong(7, event.order().pricePaisa());
            statement.setLong(8, ingestTimestamp());
            statement.setLong(9, eventTimeMs(event));
            statement.executeUpdate();
        }
    }

    private void insertFill(OrderFilled event) throws SQLException {
        long ingested = ingestTimestamp();
        long eventTime = eventTimeMs(event);
        for (var fill : event.fills()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into fills values (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, event.eventId());
                statement.setString(2, fill.orderId());
                statement.setString(3, fill.tradeId());
                statement.setString(4, fill.symbol());
                statement.setLong(5, fill.quantity());
                statement.setLong(6, fill.pricePaisa());
                statement.setLong(7, ingested);
                statement.setLong(8, eventTime);
                statement.executeUpdate();
            }
        }
    }

    private void insertFillEvent(String eventType, OrderPartiallyFilled event) throws SQLException {
        insertFillEventToTable(eventType, event, event.order(), event.fills());
    }

    private void insertFillEvent(String eventType, OrderFullyFilled event) throws SQLException {
        insertFillEventToTable(eventType, event, event.order(), event.fills());
    }

    private void insertFillEventToTable(String eventType, DomainEvent event, Order order, List<Trade> fills) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fill_events values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            String eventId = event.eventId();
            statement.setString(1, eventId);
            statement.setString(2, eventType);
            statement.setString(3, order.orderId());
            statement.setString(4, order.correlationId());
            statement.setString(5, order.symbol());
            long quantity = fills.stream().mapToLong(Trade::quantity).sum();
            statement.setLong(6, quantity);
            long avgPrice = fills.isEmpty() ? 0L
                    : fills.stream().mapToLong(Trade::pricePaisa).sum() / fills.size();
            statement.setLong(7, avgPrice);
            statement.setInt(8, fills.size());
            statement.setLong(9, order.quantity());  // total_quantity
            statement.setLong(10, order.filledQuantity());  // filled_quantity
            statement.setString(11, order.exchangeSegment().name());
            statement.setString(12, order.side().name());
            statement.setLong(13, ingestTimestamp());
            statement.setLong(14, eventTimeMs(event));
            statement.executeUpdate();
        }
    }

    private void insertTradeOpened(TradeOpened event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into trade_lifecycle (
                    event_id, event_type, trade_id, order_id, signal_id,
                    symbol, side, size, entry_price_paisa,
                    stop_loss_paisa, take_profit_paisa,
                    exit_price_paisa, realized_pnl_paisa, close_reason,
                    ingested_at_ms, event_time_ms
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, event.eventId());
            statement.setString(2, "TRADE_OPENED");
            statement.setString(3, event.tradeId());
            statement.setString(4, event.orderId());
            statement.setString(5, event.signalId() == null ? "" : event.signalId());
            statement.setString(6, event.symbol());
            statement.setString(7, event.side().name());
            statement.setLong(8, event.size());
            statement.setLong(9, event.entryPricePaisa());
            statement.setLong(10, event.stopLossPaisa());
            statement.setLong(11, event.takeProfitPaisa());
            statement.setLong(12, 0L);   // exit_price — not yet known
            statement.setLong(13, 0L);   // realized_pnl — not yet known
            statement.setString(14, ""); // close_reason — not yet known
            statement.setLong(15, ingestTimestamp());
            statement.setLong(16, eventTimeMs(event));
            statement.executeUpdate();
        }
    }

    private void insertTradeClosed(TradeClosed event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into trade_lifecycle (
                    event_id, event_type, trade_id, order_id, signal_id,
                    symbol, side, size, entry_price_paisa,
                    stop_loss_paisa, take_profit_paisa,
                    exit_price_paisa, realized_pnl_paisa, close_reason,
                    ingested_at_ms, event_time_ms
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, event.eventId());
            statement.setString(2, "TRADE_CLOSED");
            statement.setString(3, event.tradeId());
            statement.setString(4, "");   // order_id — not carried on TradeClosed
            statement.setString(5, "");   // signal_id — not carried on TradeClosed
            statement.setString(6, event.symbol());
            statement.setString(7, "");   // side — not carried on TradeClosed
            statement.setLong(8, 0L);     // size — not carried on TradeClosed
            statement.setLong(9, 0L);     // entry_price — not carried on TradeClosed
            statement.setLong(10, 0L);    // stop_loss — not carried on TradeClosed
            statement.setLong(11, 0L);    // take_profit — not carried on TradeClosed
            statement.setLong(12, event.exitPricePaisa());
            statement.setLong(13, event.realizedPnlPaisa());
            statement.setString(14, event.reason());
            statement.setLong(15, ingestTimestamp());
            statement.setLong(16, eventTimeMs(event));
            statement.executeUpdate();
        }
    }

    private long eventTimeMs(DomainEvent event) {
        long domainTime = event.timestampMs();
        return domainTime > 0L ? domainTime : ingestTimestamp();
    }

    private long ingestTimestamp() {
        return ingestedAtMs.getAsLong();
    }

    @Override
    public void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }
}
