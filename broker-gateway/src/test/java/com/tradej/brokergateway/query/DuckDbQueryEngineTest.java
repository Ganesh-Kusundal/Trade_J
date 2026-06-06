package com.tradej.brokergateway.query;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DuckDbQueryEngineTest {

    @Test
    void executesSimpleSelect() {
        try (DuckDbQueryEngine engine = new DuckDbQueryEngine()) {
            QueryResult result = engine.execute("SELECT 1 AS num, 'hello' AS msg");
            assertEquals(1, result.rowCount());
            assertEquals(2, result.columnCount());
            assertEquals(1, result.value(0, "num"));
            assertEquals("hello", result.value(0, "msg"));
        }
    }

    @Test
    void executesCreateAndQuery() {
        try (DuckDbQueryEngine engine = new DuckDbQueryEngine()) {
            engine.executeUpdate("CREATE TABLE test (id INTEGER, name VARCHAR)");
            engine.executeUpdate("INSERT INTO test VALUES (1, 'a'), (2, 'b')");
            QueryResult result = engine.execute("SELECT * FROM test ORDER BY id");
            assertEquals(2, result.rowCount());
            assertEquals(1, result.value(0, "id"));
            assertEquals("b", result.value(1, "name"));
        }
    }

    @Test
    void registerDatasourceMakesItQueryable() {
        try (DuckDbQueryEngine engine = new DuckDbQueryEngine()) {
            engine.registerDatasource("market", (conn, name) -> {
                try (var stmt = conn.createStatement()) {
                    stmt.executeUpdate("CREATE TABLE " + name + " (symbol VARCHAR, price DOUBLE)");
                    stmt.executeUpdate("INSERT INTO " + name + " VALUES ('RELIANCE', 2500.0)");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            QueryResult result = engine.execute("SELECT * FROM market");
            assertEquals(1, result.rowCount());
            assertEquals("RELIANCE", result.value(0, "symbol"));
            assertFalse(engine.datasources().isEmpty());
        }
    }

    @Test
    void queryResultMetadata() {
        try (DuckDbQueryEngine engine = new DuckDbQueryEngine()) {
            String sql = "SELECT 42 AS answer";
            QueryResult result = engine.execute(sql);
            assertEquals(sql, result.sql());
            assertTrue(result.executionTimeMs() >= 0);
            assertFalse(result.isEmpty());
            assertNotNull(result.columns());
        }
    }

    @Test
    void emptyResultSet() {
        try (DuckDbQueryEngine engine = new DuckDbQueryEngine()) {
            QueryResult result = engine.execute("SELECT 1 WHERE false");
            assertEquals(0, result.rowCount());
            assertTrue(result.isEmpty());
        }
    }
}
