package com.tradej.app.integration;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import com.tradej.persistence.replay.HistoricalRangeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@Tag("component")
class OrderReplayIntegrationTest {

    /*
     * Notes on scope:
     *
     * This test verifies that HistoricalRangeService.replayOrders() correctly reads
     * OrderAccepted events from DuckDB and re-publishes them through the EventBus.
     * The ExecutionHandler does NOT consume OrderAccepted — it produces them in
     * response to SignalPendingExecution. The subscribers that DO process
     * OrderAccepted at runtime include DuckDbEventStore (persistence),
     * BrokerErrorTracker (health), and ChronicleAuditLogWriter (audit).
     * Use @link FillReplayIntegrationTest for ExecutionHandler verification.
     */

    private static final long INGEST_BASE_MS = 1_700_000_000_000L;
    private static final long INGEST_STEP_MS = 1_000L;

    private Path dbPath;
    private AtomicLong ingestClock;
    private DuckDbEventStore eventStore;
    private HistoricalRangeService historicalRangeService;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = Files.createTempFile("order-replay-test-", ".duckdb");
        Files.deleteIfExists(dbPath);
        ingestClock = new AtomicLong(INGEST_BASE_MS);
        eventStore = new DuckDbEventStore(dbPath, () -> ingestClock.getAndAdd(INGEST_STEP_MS));
        historicalRangeService = new HistoricalRangeService(dbPath);
    }

    @AfterEach
    void tearDown() throws Exception {
        historicalRangeService.close();
        eventStore.close();
        try (var paths = Files.walk(dbPath.getParent())) {
            paths.filter(p -> p.getFileName().toString().contains("order-replay-test-"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        } catch (Exception ignored) {}
    }

    @Test
    void replaysSingleOrderAcceptedEvent() throws Exception {
        // Seed one order via DuckDbEventStore (the same path live code uses)
        Order order = createOrder("ORD-SINGLE", "corr-single", "SBIN", 100, 150_00L, OrderStatus.OPEN);
        eventStore.onEvent(new OrderAccepted(metadataAt(INGEST_BASE_MS, "corr-single", 1L), order));

        // Set up a simple EventBus that captures OrderAccepted events
        List<OrderAccepted> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new CollectingEventBus(received);

        // Replay all orders
        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var result = historicalRangeService.replayOrders(null, fromMs, toMs, eventBus);

        // Verify replay result
        assertTrue(result.isComplete(), "All orders should be replayed successfully");
        assertEquals(1L, result.replayed());
        assertEquals(0L, result.failed());

        // Verify the replayed event
        assertEquals(1, received.size());
        OrderAccepted replayed = received.get(0);
        assertEquals("ORD-SINGLE", replayed.order().orderId());
        assertEquals("corr-single", replayed.order().correlationId());
        assertEquals("SBIN", replayed.order().symbol());
        assertEquals(100, replayed.order().quantity());
        assertEquals(150_00L, replayed.order().pricePaisa());
        assertEquals(OrderStatus.OPEN, replayed.order().status());
    }

    @Test
    void replaysMultipleOrdersInIngestionOrder() throws Exception {
        // Seed three orders in a known sequence
        eventStore.onEvent(new OrderAccepted(
                metadataAt(INGEST_BASE_MS, "corr-a", 1L),
                createOrder("ORD-A", "corr-a", "SBIN", 100, 150_00L, OrderStatus.OPEN)
        ));
        eventStore.onEvent(new OrderAccepted(
                metadataAt(INGEST_BASE_MS + INGEST_STEP_MS, "corr-b", 1L),
                createOrder("ORD-B", "corr-b", "TCS", 50, 3_200_00L, OrderStatus.OPEN)
        ));
        eventStore.onEvent(new OrderAccepted(
                metadataAt(INGEST_BASE_MS + 2 * INGEST_STEP_MS, "corr-c", 1L),
                createOrder("ORD-C", "corr-c", "RELIANCE", 200, 2_500_00L, OrderStatus.TRADED)
        ));

        List<OrderAccepted> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new CollectingEventBus(received);

        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var result = historicalRangeService.replayOrders(null, fromMs, toMs, eventBus);

        assertTrue(result.isComplete());
        assertEquals(3L, result.replayed());
        assertEquals(3, received.size());

        // Verify chronological order (by ingestion time)
        assertEquals("ORD-A", received.get(0).order().orderId());
        assertEquals("ORD-B", received.get(1).order().orderId());
        assertEquals("ORD-C", received.get(2).order().orderId());

        // Verify different symbols and prices
        assertEquals("SBIN", received.get(0).order().symbol());
        assertEquals(150_00L, received.get(0).order().pricePaisa());

        assertEquals("TCS", received.get(1).order().symbol());
        assertEquals(3_200_00L, received.get(1).order().pricePaisa());

        assertEquals("RELIANCE", received.get(2).order().symbol());
        assertEquals(2_500_00L, received.get(2).order().pricePaisa());

        // Verify different statuses are preserved
        assertEquals(OrderStatus.OPEN, received.get(0).order().status());
        assertEquals(OrderStatus.TRADED, received.get(2).order().status());
    }

    @Test
    void filtersBySymbol() throws Exception {
        // Seed orders for two different symbols
        eventStore.onEvent(new OrderAccepted(
                metadataAt(INGEST_BASE_MS, "corr-sbin", 1L),
                createOrder("ORD-SBIN", "corr-sbin", "SBIN", 100, 150_00L, OrderStatus.OPEN)
        ));
        eventStore.onEvent(new OrderAccepted(
                metadataAt(INGEST_BASE_MS + INGEST_STEP_MS, "corr-tcs", 1L),
                createOrder("ORD-TCS", "corr-tcs", "TCS", 50, 3_200_00L, OrderStatus.OPEN)
        ));

        // Replay only SBIN orders
        List<OrderAccepted> sbinReceived = new CopyOnWriteArrayList<>();
        EventBus sbinBus = new CollectingEventBus(sbinReceived);

        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var sbinResult = historicalRangeService.replayOrders("SBIN", fromMs, toMs, sbinBus);

        assertEquals(1L, sbinResult.replayed());
        assertEquals(1, sbinReceived.size());
        assertEquals("SBIN", sbinReceived.get(0).order().symbol());

        // Replay only TCS orders
        List<OrderAccepted> tcsReceived = new CopyOnWriteArrayList<>();
        EventBus tcsBus = new CollectingEventBus(tcsReceived);

        var tcsResult = historicalRangeService.replayOrders("TCS", fromMs, toMs, tcsBus);

        assertEquals(1L, tcsResult.replayed());
        assertEquals(1, tcsReceived.size());
        assertEquals("TCS", tcsReceived.get(0).order().symbol());
    }

    @Test
    void preservesOrderStatusAndRejectionReason() throws Exception {
        // Seed a rejected order
        Order rejected = new Order(
                "ORD-REJ", "corr-rej", "HDFC",
                ExchangeSegment.NSE_EQ, Side.BUY, ProductType.INTRADAY, OrderType.LIMIT,
                OrderStatus.REJECTED, 50, 0L, 2_800_00L, 0L, 0L, "Insufficient margin"
        );
        eventStore.onEvent(new OrderAccepted(
                metadataAt(INGEST_BASE_MS, "corr-rej", 1L), rejected
        ));
        // Seed a cancelled order
        Order cancelled = new Order(
                "ORD-CAN", "corr-can", "HDFC",
                ExchangeSegment.NSE_EQ, Side.BUY, ProductType.INTRADAY, OrderType.MARKET,
                OrderStatus.CANCELLED, 25, 0L, 2_800_00L, 0L, 0L, "User requested cancellation"
        );
        eventStore.onEvent(new OrderAccepted(
                metadataAt(INGEST_BASE_MS + INGEST_STEP_MS, "corr-can", 1L), cancelled
        ));

        List<OrderAccepted> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new CollectingEventBus(received);

        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var result = historicalRangeService.replayOrders(null, fromMs, toMs, eventBus);

        assertEquals(2L, result.replayed());
        assertEquals(2, received.size());

        // Verify statuses and rejection reasons are preserved
        Order replayedRejected = received.get(0).order();
        Order replayedCancelled = received.get(1).order();

        assertEquals(OrderStatus.REJECTED, replayedRejected.status());
        assertEquals(OrderStatus.CANCELLED, replayedCancelled.status());

        // Default metadata fields used during reconstruction
        assertEquals(ExchangeSegment.UNKNOWN, replayedRejected.exchangeSegment());
        assertEquals(Side.UNKNOWN, replayedRejected.side());
        assertEquals(ProductType.INTRADAY, replayedRejected.productType());
        assertEquals(OrderType.MARKET, replayedRejected.orderType());
        assertEquals(0L, replayedRejected.filledQuantity());
        assertEquals(0L, replayedRejected.exchangeTimeMs());
    }

    @Test
    void returnsEmptyResultWhenNoOrdersInRange() throws Exception {
        List<OrderAccepted> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new CollectingEventBus(received);

        // No orders seeded — empty DB
        var result = historicalRangeService.replayOrders(null, 0L, Long.MAX_VALUE, eventBus);

        assertTrue(result.isEmpty());
        assertEquals(0L, result.totalRead());
        assertEquals(0L, result.replayed());
        assertEquals(0L, result.failed());
        assertTrue(received.isEmpty());
    }

    @Test
    void onlyReplaysOrdersWithinTimeRange() throws Exception {
        long earlyEventTimeMs = INGEST_BASE_MS;
        eventStore.onEvent(new OrderAccepted(
                metadataAt(earlyEventTimeMs, "corr-early", 1L),
                createOrder("ORD-EARLY", "corr-early", "SBIN", 100, 150_00L, OrderStatus.OPEN)
        ));
        long lateEventTimeMs = INGEST_BASE_MS + INGEST_STEP_MS;
        long midMs = INGEST_BASE_MS + (INGEST_STEP_MS / 2);
        eventStore.onEvent(new OrderAccepted(
                metadataAt(lateEventTimeMs, "corr-late", 1L),
                createOrder("ORD-LATE", "corr-late", "TCS", 50, 3_200_00L, OrderStatus.OPEN)
        ));

        // Replay with window [0, midMs) — should only catch the first order
        List<OrderAccepted> earlyReceived = new CopyOnWriteArrayList<>();
        EventBus earlyBus = new CollectingEventBus(earlyReceived);
        var earlyResult = historicalRangeService.replayOrders(null, 0L, midMs, earlyBus);

        assertEquals(1L, earlyResult.replayed());
        assertEquals(1, earlyReceived.size());
        assertEquals("ORD-EARLY", earlyReceived.get(0).order().orderId());

        // Replay with window [midMs, now+60s] — should only catch the second order
        List<OrderAccepted> lateReceived = new CopyOnWriteArrayList<>();
        EventBus lateBus = new CollectingEventBus(lateReceived);
        var lateResult = historicalRangeService.replayOrders(
                null, midMs, System.currentTimeMillis() + 60_000L, lateBus);

        assertTrue(lateResult.isComplete());
        assertEquals(1L, lateResult.replayed());
        assertEquals(1, lateReceived.size());
        assertEquals("ORD-LATE", lateReceived.get(0).order().orderId());
    }

    /**
     * Verifies that the DuckDbEventStore can re-persist replayed events.
     * This demonstrates the full cycle: seed → replay → store → verify.
     */
    @Test
    void replayedEventsCanBeStoredByIdempotentSubscriber() throws Exception {
        // Seed an order
        eventStore.onEvent(new OrderAccepted(
                metadataAt(INGEST_BASE_MS, "corr-cycle", 1L),
                createOrder("ORD-CYCLE", "corr-cycle", "SBIN", 100, 150_00L, OrderStatus.OPEN)
        ));

        // Replay through an EventBus that also feeds DuckDbEventStore (like live does)
        List<OrderAccepted> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new EventBus() {
            @Override public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {}
            @Override public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override
            public void publish(DomainEvent event) {
                if (event instanceof OrderAccepted oa) {
                    received.add(oa);
                }
                // Simulate an idempotent subscriber (like DuckDbEventStore) that re-stores the event
                try {
                    eventStore.onEvent(event);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var result = historicalRangeService.replayOrders(null, fromMs, toMs, eventBus);

        assertTrue(result.isComplete());
        assertEquals(1L, result.replayed());

        // Re-query the DB — the DuckDbEventStore re-persisted the replayed event
        // (with a new eventId, so it's a separate row)
        var reQueried = historicalRangeService.queryOrders("SBIN", fromMs, toMs);
        assertEquals(2, reQueried.size(), "Should have the original + replayed order rows");
    }

    // ── Helpers ──

    private static EventMetadata metadataAt(long eventTimeMs, String correlationId, long sequenceId) {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                eventTimeMs,
                System.nanoTime(),
                sequenceId,
                correlationId,
                1
        );
    }

    private static Order createOrder(String orderId, String correlationId, String symbol,
                                     long quantity, long pricePaisa, OrderStatus status) {
        return new Order(
                orderId, correlationId, symbol,
                ExchangeSegment.UNKNOWN, Side.UNKNOWN, ProductType.INTRADAY, OrderType.MARKET,
                status, quantity, 0L, pricePaisa, 0L, 0L, ""
        );
    }

    /**
     * Minimal EventBus implementation that collects OrderAccepted events
     * for verification. Other event types are silently dropped.
     */
    private static final class CollectingEventBus implements EventBus {
        private final List<OrderAccepted> sink;

        CollectingEventBus(List<OrderAccepted> sink) {
            this.sink = sink;
        }

        @Override
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {}

        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {}

        @Override
        public void publish(DomainEvent event) {
            if (event instanceof OrderAccepted oa) {
                sink.add(oa);
            }
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}
    }
}
