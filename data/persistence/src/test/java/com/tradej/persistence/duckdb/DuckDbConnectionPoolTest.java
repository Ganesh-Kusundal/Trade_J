package com.tradej.persistence.duckdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DuckDbConnectionPoolTest {

    private DuckDbConnectionPool pool;

    @BeforeEach
    void setUp() {
        pool = DuckDbConnectionPool.inMemory();
    }

    @AfterEach
    void tearDown() {
        pool.close();
    }

    @Test
    void withConnection_executesFunction() {
        Integer result = pool.withConnection(conn -> {
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 42")) {
                rs.next();
                return rs.getInt(1);
            }
        });
        assertEquals(42, result);
    }

    @Test
    void withConnectionVoid_executesAction() {
        pool.withConnectionVoid(conn -> {
            try (var stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INTEGER, name VARCHAR)");
                stmt.execute("INSERT INTO test VALUES (1, 'hello')");
            }
        });

        String name = pool.withConnection(conn -> {
            try (var stmt = conn.prepareStatement("SELECT name FROM test WHERE id = 1");
                 var rs = stmt.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        });
        assertEquals("hello", name);
    }

    @Test
    void concurrentReads_areSerialized() throws Exception {
        pool.withConnectionVoid(conn -> {
            try (var stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE items (id INTEGER, value INTEGER)");
                for (int i = 0; i < 100; i++) {
                    stmt.execute("INSERT INTO items VALUES (" + i + ", " + (i * 10) + ")");
                }
            }
        });

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 25; i++) {
                        Integer sum = pool.withConnection(conn -> {
                            try (var stmt = conn.createStatement();
                                 var rs = stmt.executeQuery("SELECT SUM(value) FROM items")) {
                                rs.next();
                                return rs.getInt(1);
                            }
                        });
                        if (sum != null && sum > 0) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(0, errorCount.get(), "No errors expected from concurrent reads");
        assertEquals(100, successCount.get(), "All 100 reads should succeed");
    }

    @Test
    void close_preventsSubsequentAccess() {
        pool.close();
        assertTrue(pool.isClosed());
        assertThrows(IllegalStateException.class, () ->
                pool.withConnection(conn -> null));
    }

    @Test
    void readOnlyConnection_canQuery(@TempDir Path tempDir) throws Exception {
        // In-memory DuckDB is per-connection, so use file-based for shared read-only test
        DuckDbConnectionPool filePool = DuckDbConnectionPool.create(tempDir.resolve("test.duckdb"));
        filePool.withConnectionVoid(conn -> {
            try (var stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE readonly_test (val INTEGER)");
                stmt.execute("INSERT INTO readonly_test VALUES (99)");
            }
        });

        try (Connection roConn = filePool.readOnlyConnection();
             var stmt = roConn.createStatement();
             var rs = stmt.executeQuery("SELECT val FROM readonly_test")) {
            assertTrue(rs.next());
            assertEquals(99, rs.getInt(1));
        }
        filePool.close();
    }

    @Test
    void rawConnection_returnsUnderlyingConnection() {
        assertNotNull(pool.rawConnection());
    }
}
