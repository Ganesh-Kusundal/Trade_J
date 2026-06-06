package com.tradej.brokergateway.query;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DuckDbQueryEngineTransactionalTest {

    private DuckDbQueryEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DuckDbQueryEngine();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void registerDatasource_successfulRegistration_works() {
        engine.registerDatasource("test_ds", (conn, name) -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_table AS SELECT 42 AS value");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Verify the datasource is registered
        assertTrue(engine.datasources().containsKey("test_ds"));

        // Verify the table is queryable
        QueryResult result = engine.execute("SELECT value FROM test_table");
        assertEquals(1, result.rows().size());
        assertEquals(42, result.rows().get(0).get("value"));
    }

    @Test
    void registerDatasource_failedRegistration_doesNotRegister() {
        // First, create a table so we can verify the engine remains usable
        engine.executeUpdate("CREATE TABLE persistent_table AS SELECT 1 AS id");

        // Attempt a registration that fails mid-way
        assertThrows(IllegalStateException.class, () ->
            engine.registerDatasource("bad_ds", (conn, name) -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE TABLE should_not_exist AS SELECT 99 AS value");
                    // Now simulate a failure
                    throw new RuntimeException("Simulated registration failure");
                } catch (Exception e) {
                    if (e instanceof RuntimeException re) throw re;
                    throw new RuntimeException(e);
                }
            })
        );

        // The bad datasource should NOT be registered
        assertFalse(engine.datasources().containsKey("bad_ds"));

        // The engine should still be usable — the pre-existing table should still work
        QueryResult result = engine.execute("SELECT id FROM persistent_table");
        assertEquals(1, result.rows().size());
    }

    @Test
    void registerDatasource_firstFails_secondSucceeds() {
        // First registration fails
        assertThrows(IllegalStateException.class, () ->
            engine.registerDatasource("first_ds", (conn, name) -> {
                throw new RuntimeException("First datasource fails");
            })
        );

        // Second registration succeeds
        engine.registerDatasource("second_ds", (conn, name) -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE second_table AS SELECT 'hello' AS greeting");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Only the second datasource should be registered
        assertFalse(engine.datasources().containsKey("first_ds"));
        assertTrue(engine.datasources().containsKey("second_ds"));

        // The second datasource's table should be queryable
        QueryResult result = engine.execute("SELECT greeting FROM second_table");
        assertEquals(1, result.rows().size());
        assertEquals("hello", result.rows().get(0).get("greeting"));
    }
}
