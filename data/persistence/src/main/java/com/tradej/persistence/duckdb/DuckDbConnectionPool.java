package com.tradej.persistence.duckdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Thread-safe DuckDB connection pool.
 *
 * <p>DuckDB is an in-process analytical database that supports concurrent reads
 * but serializes writes. This pool manages a single shared connection with
 * lock-based access to prevent concurrent write contention.
 *
 * <p>Usage:
 * <pre>
 *   DuckDbConnectionPool pool = DuckDbConnectionPool.create(Path.of("runtime/tradej.duckdb"));
 *   List&lt;Candle&gt; candles = pool.withConnection(conn -> {
 *       try (var stmt = conn.prepareStatement("SELECT * FROM candles WHERE symbol = ?")) {
 *           stmt.setString(1, "RELIANCE");
 *           try (var rs = stmt.executeQuery()) {
 *               return mapResults(rs);
 *           }
 *       }
 *   });
 *   pool.close();
 * </pre>
 *
 * <p>For read-heavy workloads, consider creating separate read-only connections
 * via {@link #readOnlyConnection()} which can run concurrently.
 */
public final class DuckDbConnectionPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DuckDbConnectionPool.class);

    private Connection connection;
    private final Path databasePath;
    private final ReentrantLock writeLock = new ReentrantLock();
    private volatile boolean closed = false;

    private DuckDbConnectionPool(Connection connection, Path databasePath) {
        this.connection = connection;
        this.databasePath = databasePath;
    }

    /**
     * Create a pool backed by a file-based DuckDB database.
     */
    public static DuckDbConnectionPool create(Path databasePath) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:duckdb:" + databasePath.toAbsolutePath());
            log.info("DuckDbConnectionPool created at {}", databasePath);
            return new DuckDbConnectionPool(conn, databasePath);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create DuckDB connection at " + databasePath, e);
        }
    }

    /**
     * Create an in-memory pool (for testing).
     */
    public static DuckDbConnectionPool inMemory() {
        try {
            Connection conn = DriverManager.getConnection("jdbc:duckdb:");
            return new DuckDbConnectionPool(conn, null);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create in-memory DuckDB connection", e);
        }
    }

    /**
     * Execute a function with exclusive access to the shared connection.
     * Thread-safe — only one thread can access the connection at a time.
     *
     * @param action function that receives the connection and returns a result
     * @return the result of the action
     */
    public <T> T withConnection(ConnectionFunction<T> action) {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }
        writeLock.lock();
        try {
            ensureConnection();
            return action.apply(connection);
        } catch (SQLException e) {
            throw new DuckDbException("DuckDB operation failed", e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Execute a void action with exclusive access to the shared connection.
     */
    public void withConnectionVoid(ConnectionAction action) {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }
        writeLock.lock();
        try {
            ensureConnection();
            action.execute(connection);
        } catch (SQLException e) {
            throw new DuckDbException("DuckDB operation failed", e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Create a separate read-only connection for concurrent reads.
     * Caller is responsible for closing the returned connection.
     */
    public Connection readOnlyConnection() {
        try {
            String url = databasePath != null
                    ? "jdbc:duckdb:" + databasePath.toAbsolutePath()
                    : "jdbc:duckdb:";
            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            throw new DuckDbException("Failed to create DuckDB connection", e);
        }
    }

    /**
     * Returns the underlying connection for legacy code that needs direct access.
     * Use {@link #withConnection(ConnectionFunction)} for new code.
     */
    public Connection rawConnection() {
        writeLock.lock();
        try {
            ensureConnection();
            return connection;
        } finally {
            writeLock.unlock();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        writeLock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("DuckDbConnectionPool closed");
            }
        } catch (SQLException e) {
            log.warn("Error closing DuckDB connection: {}", e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    private void ensureConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                log.warn("DuckDB connection lost — reconnecting to {}", databasePath);
                String url = databasePath != null
                        ? "jdbc:duckdb:" + databasePath.toAbsolutePath()
                        : "jdbc:duckdb:";
                this.connection = DriverManager.getConnection(url);
                log.info("DuckDB reconnected successfully");
            }
        } catch (SQLException e) {
            throw new DuckDbException("Failed to reconnect DuckDB connection", e);
        }
    }

    @FunctionalInterface
    public interface ConnectionFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface ConnectionAction {
        void execute(Connection connection) throws SQLException;
    }

    public static final class DuckDbException extends RuntimeException {
        public DuckDbException(String message) {
            super(message);
        }
        public DuckDbException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
