package com.tradej.feature.store;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.FeatureGenerator;
import com.tradej.core.domain.model.FeatureVector;
import com.tradej.core.domain.port.FeatureStore;
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
import java.util.Optional;

/**
 * DuckDB-backed time-series feature store.
 *
 * <p>Ingests ticks and candles into DuckDB tables and computes feature vectors
 * (RSI, EMA, SMA, VWAP, volatility, volume imbalance) from the stored data.
 */
public final class DuckDbFeatureStore implements FeatureStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DuckDbFeatureStore.class);

    private final Path databasePath;
    private Connection connection;

    public DuckDbFeatureStore(Path databasePath) {
        this.databasePath = databasePath;
        initConnection();
    }

    DuckDbFeatureStore(Connection connection) {
        this.databasePath = null;
        this.connection = connection;
        try {
            bootstrap();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to bootstrap feature store tables", e);
        }
    }

    private void initConnection() {
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:" + databasePath.toAbsolutePath());
            bootstrap();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize DuckDB feature store at " + databasePath, e);
        }
    }

    // Throttle ensureConnection: only validate the connection every N events,
    // since isValid(2) sends a JDBC round-trip. Rely on SQLException from the
    // actual operation to trigger a reconnection if needed (fixes FS-01).
    private static final int CONNECTION_CHECK_INTERVAL = 1000;
    private int eventCounter;

    /**
     * Validates the connection and reconnects if broken.
     * Throttled to check only every {@link #CONNECTION_CHECK_INTERVAL} events
     * to avoid a JDBC round-trip on every tick/candle hot-path.
     */
    private synchronized void ensureConnection() throws SQLException {
        if (databasePath == null) {
            return; // injected connection — don't manage lifecycle
        }
        eventCounter++;
        if (eventCounter < CONNECTION_CHECK_INTERVAL && connection != null && !connection.isClosed()) {
            return; // Connection was recently validated — skip expensive check
        }
        eventCounter = 0;
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            log.warn("DuckDB feature store connection lost — reconnecting");
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (Exception ignored) {
                // ignore
            }
            initConnection();
            log.info("DuckDB feature store reconnected successfully");
        }
    }

    // ── FeatureStore API ──

    @Override
    public synchronized void feed(DomainEvent event) {
        try {
            ensureConnection();
            switch (event) {
                case MarketTickEvent tick -> insertMarketTick(tick);
                case CandleDeveloping dev -> upsertCandle(dev.candle(), false);
                case CandleClosed closed -> upsertCandle(closed.candle(), true);
                default -> { /* unsupported event type, silently ignored */ }
            }
        } catch (SQLException e) {
            log.warn("Failed to ingest event type={} id={}: {}", event.getClass().getSimpleName(), event.eventId(), e.getMessage());
        }
    }

    @Override
    public synchronized Optional<FeatureVector> getFeatures(String symbol, String interval, int lookback) {
        try {
            ensureConnection();
            int fetchCount = Math.max(lookback + 1, 5);
            List<Candle> candles = queryCandles(symbol, interval, fetchCount);
            if (candles.size() < Math.min(lookback, 5)) {
                return Optional.empty();
            }
            return FeatureGenerator.compute(candles, lookback);
        } catch (SQLException e) {
            log.warn("Failed to query features for {} {}: {}", symbol, interval, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    // ── Table bootstrap ──

    private void bootstrap() throws SQLException {
        connection.createStatement().execute("""
                create table if not exists feature_ticks (
                    event_id varchar,
                    symbol varchar,
                    interval varchar,
                    ltp_paisa bigint,
                    last_trade_quantity bigint,
                    cumulative_volume bigint,
                    exchange_timestamp_ms bigint,
                    ingested_at_ms bigint,
                    exchange_segment varchar,
                    depth_json varchar
                )
                """);
        migrateFeatureTicksColumns();
        connection.createStatement().execute("""
                create table if not exists feature_candles (
                    event_id varchar,
                    symbol varchar,
                    interval varchar,
                    start_time_ms bigint,
                    end_time_ms bigint,
                    open_paisa bigint,
                    high_paisa bigint,
                    low_paisa bigint,
                    close_paisa bigint,
                    volume bigint,
                    closed boolean,
                    ingested_at_ms bigint,
                    primary key (symbol, interval, start_time_ms)
                )
                """);
    }

    private void migrateFeatureTicksColumns() throws SQLException {
        try {
            connection.createStatement().execute(
                    "alter table feature_ticks add column if not exists exchange_segment varchar");
            connection.createStatement().execute(
                    "alter table feature_ticks add column if not exists depth_json varchar");
        } catch (SQLException ignored) {
            // Legacy table layout without new columns
        }
    }

    // ── Insert / upsert ──

    private void insertMarketTick(MarketTickEvent tick) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into feature_ticks values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, tick.eventId());
            ps.setString(2, tick.symbol());
            ps.setString(3, "");
            ps.setLong(4, tick.ltpPaisa());
            ps.setLong(5, tick.lastTradeQuantity());
            ps.setLong(6, tick.cumulativeVolume());
            ps.setLong(7, tick.exchangeTimestampEpochMs());
            ps.setLong(8, System.currentTimeMillis());
            ps.setString(9, tick.segment() != null ? tick.segment().name() : null);
            ps.setString(10, tick.depth().map(d -> "").orElse(null));
            ps.executeUpdate();
        }
    }


    private void upsertCandle(Candle candle, boolean closed) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into feature_candles values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (symbol, interval, start_time_ms) do update set
                    close_paisa = excluded.close_paisa,
                    high_paisa = excluded.high_paisa,
                    low_paisa = excluded.low_paisa,
                    volume = excluded.volume,
                    closed = excluded.closed,
                    ingested_at_ms = excluded.ingested_at_ms
                """)) {
            // Use a deterministic event_id derived from the candle data instead of empty string
            ps.setString(1, closed ? candle.symbol() + "@" + candle.startTimeMs() : "");
            ps.setString(2, candle.symbol());
            ps.setString(3, candle.interval());
            ps.setLong(4, candle.startTimeMs());
            ps.setLong(5, candle.endTimeMs());
            ps.setLong(6, candle.openPaisa());
            ps.setLong(7, candle.highPaisa());
            ps.setLong(8, candle.lowPaisa());
            ps.setLong(9, candle.closePaisa());
            ps.setLong(10, candle.volume());
            ps.setBoolean(11, closed);
            ps.setLong(12, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    // ── Query ──

    private List<Candle> queryCandles(String symbol, String interval, int limit) throws SQLException {
        List<Candle> candles = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                select start_time_ms, end_time_ms, open_paisa, high_paisa, low_paisa,
                       close_paisa, volume, closed
                from feature_candles
                where symbol = ? and interval = ?
                order by start_time_ms desc
                limit ?
                """)) {
            ps.setString(1, symbol);
            ps.setString(2, interval);
            ps.setInt(3, limit);
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
                            rs.getBoolean("closed")
                    ));
                }
            }
        }
        // Return in chronological order (oldest first) for feature computation
        java.util.Collections.reverse(candles);
        return candles;
    }
}
