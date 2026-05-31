package com.tradej.persistence.duckdb;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderFullyFilled;
import com.tradej.core.domain.event.OrderPartiallyFilled;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.testing.ConcurrentStressTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DuckDbEventStoreTest {

    private Path dbPath;
    private DuckDbEventStore store;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = Files.createTempFile("duckdb-test-", ".duckdb");
        Files.deleteIfExists(dbPath);
        store = new DuckDbEventStore(dbPath);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        Files.deleteIfExists(dbPath);
    }

    @Test
    void persistsOrderPartiallyFilledEvent() throws Exception {
        Order order = createOrder("ORD-001", "corr-1", "SBIN", 200);
        List<Trade> fills = List.of(
                new Trade("T-001", "ORD-001", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 50, 151_00L, 1000L)
        );
        var event = new OrderPartiallyFilled(
                EventMetadata.correlated("corr-1", 1L),
                order,
                fills
        );

        store.onEvent(event);

        assertFillEventPersisted("PARTIALLY_FILLED", "ORD-001", "SBIN", 50L, 151_00L, 1);
    }

    @Test
    void persistsOrderFullyFilledEvent() throws Exception {
        Order order = createOrder("ORD-002", "corr-2", "TCS", 100);
        List<Trade> fills = List.of(
                new Trade("T-002", "ORD-002", "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 100, 3_200_00L, 2000L)
        );
        var event = new OrderFullyFilled(
                EventMetadata.correlated("corr-2", 1L),
                order,
                fills
        );

        store.onEvent(event);

        assertFillEventPersisted("FULLY_FILLED", "ORD-002", "TCS", 100L, 3_200_00L, 1);
    }

    @Test
    void persistsMultipleFillEventsInOrder() throws Exception {
        Order order = createOrder("ORD-003", "corr-3", "RELIANCE", 200);

        // First partial fill of 50
        List<Trade> fill1 = List.of(
                new Trade("T-003", "ORD-003", "RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY, 50, 2_500_00L, 3000L)
        );
        store.onEvent(new OrderPartiallyFilled(
                EventMetadata.correlated("corr-3", 1L), order, fill1
        ));

        // Second partial fill of 100
        List<Trade> fill2 = List.of(
                new Trade("T-004", "ORD-003", "RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY, 100, 2_510_00L, 4000L)
        );
        store.onEvent(new OrderPartiallyFilled(
                EventMetadata.correlated("corr-3", 2L), order, fill2
        ));

        // Full fill of remaining 50
        List<Trade> fill3 = List.of(
                new Trade("T-005", "ORD-003", "RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY, 50, 2_520_00L, 5000L)
        );
        store.onEvent(new OrderFullyFilled(
                EventMetadata.correlated("corr-3", 3L), order, fill3
        ));

        // Verify all three fill events were persisted
        assertFillEventCount("ORD-003", 3);
        assertFillEventPersisted("PARTIALLY_FILLED", "ORD-003", "RELIANCE", 50L, 2_500_00L, 1);
        assertFillEventPersisted("PARTIALLY_FILLED", "ORD-003", "RELIANCE", 100L, 2_510_00L, 1);
        assertFillEventPersisted("FULLY_FILLED", "ORD-003", "RELIANCE", 50L, 2_520_00L, 1);
    }

    @Test
    void persistsOrderAcceptedEvent() throws Exception {
        Order order = createOrder("ORD-004", "corr-4", "HDFC", 50);
        var event = new OrderAccepted(
                EventMetadata.correlated("corr-4", 1L),
                order
        );

        store.onEvent(event);

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("select count(*) as cnt from orders")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("cnt"));
        }
    }

    @Test
    void persistsOrderFilledEvent() throws Exception {
        Order order = createOrder("ORD-005", "corr-5", "SBIN", 100);
        List<Trade> fills = List.of(
                new Trade("T-006", "ORD-005", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 150_00L, 6000L)
        );
        var event = new OrderFilled(
                EventMetadata.correlated("corr-5", 1L),
                order,
                fills
        );

        store.onEvent(event);

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("select count(*) as cnt from fills")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("cnt"));
        }
    }

    @Test
    void autoRecoversAfterConnectionClosed() throws Exception {
        // Close the underlying JDBC connection
        store.close();

        // The connection is now closed — onEvent should trigger ensureConnection() which reconnects
        Order order = createOrder("ORD-RECOV", "corr-recovery", "SBIN", 100);
        var event = new OrderAccepted(EventMetadata.correlated("corr-recovery", 1L), order);
        store.onEvent(event);

        // Verify event was persisted through the recovered connection
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("select count(*) as cnt from orders")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("cnt"),
                    "Order should persist after auto-reconnect");
        }
    }

    @Test
    @Tag("stress")
    void concurrentWritesThroughRecoveredConnection() throws Exception {
        // Close the underlying connection first — tests concurrent recovery
        store.close();

        int workers = 8;
        int eventsPerWorker = 25;
        int expectedEvents = workers * eventsPerWorker;

        var result = ConcurrentStressTester.run(workers, eventsPerWorker, threadIndex -> {
            try {
                String orderId = "ORD-STRESS-" + threadIndex + "-" + (int) (Math.random() * 10000);
                Order order = new Order(
                        orderId, "corr-" + orderId, "SBIN", ExchangeSegment.NSE_EQ,
                        Side.BUY, ProductType.INTRADAY, OrderType.LIMIT,
                        OrderStatus.OPEN, 100, 0L, 150_00L, 0L, 1000L, ""
                );
                store.onEvent(new OrderAccepted(
                        EventMetadata.correlated("corr-" + orderId, 1L), order
                ));
            } catch (Exception e) {
                throw new RuntimeException("Failed to persist event", e);
            }
        }, Duration.ofSeconds(30));

        result.assertAllPassed();

        // Verify all events were persisted
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("select count(*) as cnt from orders")) {
            assertTrue(rs.next());
            assertEquals(expectedEvents, rs.getInt("cnt"),
                    "Expected " + expectedEvents + " events after concurrent writes through recovered connection");
        }
    }

    @Test
    void fillEventsTableHasCorrectSchema() throws Exception {
        // Verify the fill_events table was created by bootstrap
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("select column_name, data_type from information_schema.columns " +
                     "where table_name = 'fill_events' order by ordinal_position")) {

            List<String> columns = new ArrayList<>();
            while (rs.next()) {
                columns.add(rs.getString("column_name"));
            }
            assertTrue(columns.contains("event_id"));
            assertTrue(columns.contains("event_type"));
            assertTrue(columns.contains("order_id"));
            assertTrue(columns.contains("correlation_id"));
            assertTrue(columns.contains("symbol"));
            assertTrue(columns.contains("quantity"));
            assertTrue(columns.contains("price_paisa"));
            assertTrue(columns.contains("fill_count"));
            assertTrue(columns.contains("total_quantity"),
                    "fill_events should have total_quantity column");
            assertTrue(columns.contains("filled_quantity"),
                    "fill_events should have filled_quantity column");
            assertTrue(columns.contains("exchange_segment"),
                    "fill_events should have exchange_segment column");
            assertTrue(columns.contains("side"),
                    "fill_events should have side column");
        }
    }

    // ── Helpers ──

    private void assertFillEventPersisted(String eventType, String orderId, String symbol,
                                          long quantity, long pricePaisa, int fillCount) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select event_type, order_id, symbol, quantity, price_paisa, fill_count " +
                             "from fill_events " +
                             "where order_id = '" + orderId + "' " +
                             "and event_type = '" + eventType + "' " +
                             "and quantity = " + quantity + " " +
                             "and price_paisa = " + pricePaisa)) {

            assertTrue(rs.next(),
                    "Expected fill_events row: type=" + eventType + " order=" + orderId
                            + " qty=" + quantity + " price=" + pricePaisa);
            assertEquals(eventType, rs.getString("event_type"));
            assertEquals(orderId, rs.getString("order_id"));
            assertEquals(symbol, rs.getString("symbol"));
            assertEquals(quantity, rs.getLong("quantity"));
            assertEquals(pricePaisa, rs.getLong("price_paisa"));
            assertEquals(fillCount, rs.getInt("fill_count"));
        }
    }

    private void assertFillEventCount(String orderId, int expectedCount) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select count(*) as cnt from fill_events where order_id = '" + orderId + "'")) {
            assertTrue(rs.next());
            assertEquals(expectedCount, rs.getInt("cnt"),
                    "Expected " + expectedCount + " fill events for order " + orderId);
        }
    }

    private static Order createOrder(String orderId, String correlationId, String symbol, long quantity) {
        return new Order(
                orderId, correlationId, symbol, ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, com.tradej.core.domain.value.OrderType.LIMIT,
                OrderStatus.OPEN, quantity, 0L, 150_00L, 0L, 1000L, ""
        );
    }
}
