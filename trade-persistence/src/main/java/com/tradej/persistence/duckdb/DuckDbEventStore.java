package com.tradej.persistence.duckdb;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.port.DomainEventHandler;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class DuckDbEventStore implements DomainEventHandler<DomainEvent>, AutoCloseable {
    private final Connection connection;

    public DuckDbEventStore(Path databasePath) {
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:" + databasePath.toAbsolutePath());
            bootstrap();
        } catch (SQLException sqlException) {
            throw new IllegalStateException("Unable to initialize DuckDB store", sqlException);
        }
    }

    @Override
    public void onEvent(DomainEvent event) throws Exception {
        if (event instanceof CandleClosed candleClosed) {
            insertCandle(candleClosed);
        } else if (event instanceof OrderAccepted orderAccepted) {
            insertOrder(orderAccepted);
        } else if (event instanceof OrderFilled orderFilled) {
            insertFill(orderFilled);
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
                    price_paisa bigint
                )
                """);
        connection.createStatement().execute("""
                create table if not exists fills (
                    event_id varchar,
                    order_id varchar,
                    trade_id varchar,
                    symbol varchar,
                    quantity bigint,
                    price_paisa bigint
                )
                """);
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
                insert into orders values (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, event.eventId());
            statement.setString(2, event.order().orderId());
            statement.setString(3, event.order().correlationId());
            statement.setString(4, event.order().symbol());
            statement.setString(5, event.order().status().name());
            statement.setLong(6, event.order().quantity());
            statement.setLong(7, event.order().pricePaisa());
            statement.executeUpdate();
        }
    }

    private void insertFill(OrderFilled event) throws SQLException {
        for (var fill : event.fills()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into fills values (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, event.eventId());
                statement.setString(2, fill.orderId());
                statement.setString(3, fill.tradeId());
                statement.setString(4, fill.symbol());
                statement.setLong(5, fill.quantity());
                statement.setLong(6, fill.pricePaisa());
                statement.executeUpdate();
            }
        }
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
