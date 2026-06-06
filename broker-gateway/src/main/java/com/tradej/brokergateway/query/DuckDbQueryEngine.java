package com.tradej.brokergateway.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight DuckDB SQL engine for ad-hoc queries over market data.
 *
 * <p>Wraps a DuckDB JDBC connection and provides a simple execute(sql) API
 * that returns {@link QueryResult} records. Data sources can be registered
 * to create views over broker historical data, parquet files, etc.
 *
 * <p>Usage:
 * <pre>
 *   try (DuckDbQueryEngine engine = new DuckDbQueryEngine()) {
 *       engine.registerDatasource("candles", (conn, name) -&gt; { ... });
 *       QueryResult result = engine.execute("SELECT * FROM candles WHERE symbol = 'RELIANCE'");
 *   }
 * </pre>
 */
public final class DuckDbQueryEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DuckDbQueryEngine.class);

    private final Connection connection;
    private final Map<String, MarketDatasource> datasources;

    /**
     * Create an in-memory DuckDB engine.
     */
    public DuckDbQueryEngine() {
        this(":memory:");
    }

    /**
     * Create a DuckDB engine backed by a file at the given path.
     */
    public DuckDbQueryEngine(Path duckdbPath) {
        this(duckdbPath.toString());
    }

    private DuckDbQueryEngine(String path) {
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:" + path);
            this.datasources = new LinkedHashMap<>();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open DuckDB connection: " + path, e);
        }
    }

    /**
     * Register a named datasource. The datasource's tables/views become
     * available for subsequent SQL queries.
     */
    public void registerDatasource(String name, MarketDatasource ds) {
        ds.register(connection, name);
        datasources.put(name, ds);
        log.debug("Registered datasource: {}", name);
    }

    /**
     * Execute a SQL query and return the result as a {@link QueryResult}.
     */
    public QueryResult execute(String sql) {
        long start = System.currentTimeMillis();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            List<String> columns = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    row.put(columns.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
            }

            long elapsed = System.currentTimeMillis() - start;
            return new QueryResult(columns, rows, elapsed, sql);

        } catch (SQLException e) {
            throw new IllegalArgumentException("SQL execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a DDL or DML statement (CREATE, INSERT, DROP, etc.) that does not return a result set.
     */
    public void executeUpdate(String sql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalArgumentException("SQL update failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the names of all registered datasources.
     */
    public Map<String, MarketDatasource> datasources() {
        return Map.copyOf(datasources);
    }

    /**
     * Returns the underlying JDBC connection for advanced use.
     */
    public Connection connection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("Error closing DuckDB connection", e);
        }
    }
}
