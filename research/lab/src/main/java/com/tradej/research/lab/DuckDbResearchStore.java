package com.tradej.research.lab;

import com.tradej.persistence.duckdb.DuckDbConnectionPool;
import com.tradej.research.core.RunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DuckDbResearchStore implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DuckDbResearchStore.class);
    private final DuckDbConnectionPool pool;

    public DuckDbResearchStore() {
        this.pool = DuckDbConnectionPool.create(Path.of("research.db"));
        initSchema();
    }

    public DuckDbResearchStore(String dbUrl) {
        this.pool = createPoolFromUrl(dbUrl);
        initSchema();
    }

    private static DuckDbConnectionPool createPoolFromUrl(String dbUrl) {
        String prefix = "jdbc:duckdb:";
        if (!dbUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("Not a DuckDB JDBC URL: " + dbUrl);
        }
        String remainder = dbUrl.substring(prefix.length());
        if (remainder.isEmpty() || remainder.equals(":memory:")) {
            return DuckDbConnectionPool.inMemory();
        }
        return DuckDbConnectionPool.create(Path.of(remainder));
    }

    private void initSchema() {
        pool.withConnectionVoid(conn -> {
            try (var stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS candles (
                        symbol VARCHAR,
                        bar_time_ms BIGINT,
                        open_paisa BIGINT,
                        high_paisa BIGINT,
                        low_paisa BIGINT,
                        close_paisa BIGINT,
                        volume BIGINT,
                        PRIMARY KEY (symbol, bar_time_ms)
                    )""");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS scanner_hits (
                        session_id VARCHAR,
                        config_hash VARCHAR,
                        symbol VARCHAR,
                        hit_time_ms BIGINT,
                        criteria_met VARCHAR
                    )""");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS trade_log (
                        run_id VARCHAR,
                        trade_id VARCHAR,
                        symbol VARCHAR,
                        side VARCHAR,
                        entry_time_ms BIGINT,
                        entry_price_paisa BIGINT,
                        exit_time_ms BIGINT,
                        exit_price_paisa BIGINT,
                        quantity BIGINT,
                        realized_pnl_paisa BIGINT
                    )""");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS run_results (
                        run_id VARCHAR,
                        session_id VARCHAR,
                        config_hash VARCHAR,
                        start_time_ms BIGINT,
                        end_time_ms BIGINT,
                        total_trades BIGINT,
                        win_rate DOUBLE,
                        total_profit_loss DOUBLE,
                        sharpe_ratio DOUBLE,
                        sortino_ratio DOUBLE,
                        max_drawdown DOUBLE
                    )""");

                log.info("DuckDB Research schemas initialized successfully.");
            }
        });
    }

    public Connection getConnection() throws SQLException {
        return pool.rawConnection();
    }

    public synchronized void saveRunResult(RunResult runResult) {
        String sql = """
            INSERT INTO run_results (run_id, session_id, config_hash, start_time_ms, end_time_ms,
                                     total_trades, win_rate, total_profit_loss, sharpe_ratio, sortino_ratio, max_drawdown)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        pool.withConnectionVoid(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, runResult.runId());
                ps.setString(2, runResult.sessionId());
                ps.setString(3, runResult.configHash());
                ps.setLong(4, runResult.startTimeMs());
                ps.setLong(5, runResult.endTimeMs());
                ps.setLong(6, runResult.totalTrades());
                ps.setDouble(7, runResult.winRate());
                ps.setDouble(8, runResult.totalProfitLoss());
                ps.setDouble(9, runResult.sharpeRatio());
                ps.setDouble(10, runResult.sortinoRatio());
                ps.setDouble(11, runResult.maxDrawdown());
                ps.executeUpdate();
            }
        });
    }

    public synchronized void saveScannerHit(String sessionId, String configHash, String symbol, long hitTimeMs, String criteriaMet) {
        String sql = """
            INSERT INTO scanner_hits (session_id, config_hash, symbol, hit_time_ms, criteria_met)
            VALUES (?, ?, ?, ?, ?)
        """;
        pool.withConnectionVoid(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sessionId);
                ps.setString(2, configHash);
                ps.setString(3, symbol);
                ps.setLong(4, hitTimeMs);
                ps.setString(5, criteriaMet);
                ps.executeUpdate();
            }
        });
    }

    public synchronized void saveTradeLog(
        String runId,
        String tradeId,
        String symbol,
        String side,
        long entryTimeMs,
        long entryPricePaisa,
        long exitTimeMs,
        long exitPricePaisa,
        long quantity,
        long realizedPnlPaisa
    ) {
        String sql = """
            INSERT INTO trade_log (run_id, trade_id, symbol, side, entry_time_ms, entry_price_paisa,
                                   exit_time_ms, exit_price_paisa, quantity, realized_pnl_paisa)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        pool.withConnectionVoid(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, runId);
                ps.setString(2, tradeId);
                ps.setString(3, symbol);
                ps.setString(4, side);
                ps.setLong(5, entryTimeMs);
                ps.setLong(6, entryPricePaisa);
                ps.setLong(7, exitTimeMs);
                ps.setLong(8, exitPricePaisa);
                ps.setLong(9, quantity);
                ps.setLong(10, realizedPnlPaisa);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void close() {
        pool.close();
    }
}
