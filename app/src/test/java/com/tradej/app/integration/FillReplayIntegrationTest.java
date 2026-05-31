package com.tradej.app.integration;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderFullyFilled;
import com.tradej.core.domain.event.OrderPartiallyFilled;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link HistoricalRangeService#replayFillEvents}.
 *
 * <p>Seeds fill events via {@link DuckDbEventStore#onEvent(DomainEvent)} (the same
 * path live code uses), then replays through a minimal {@link EventBus} to verify
 * that {@link OrderPartiallyFilled} and {@link OrderFullyFilled} events are correctly
 * reconstructed from the {@code fill_events} DuckDB table.
 *
 * @see OrderReplayIntegrationTest
 */
@Tag("component")
class FillReplayIntegrationTest {

    private static final long INGEST_BASE_MS = 1_700_000_000_000L;
    private static final long INGEST_STEP_MS = 1_000L;

    private Path dbPath;
    private AtomicLong ingestClock;
    private DuckDbEventStore eventStore;
    private HistoricalRangeService historicalRangeService;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = Files.createTempFile("fill-replay-test-", ".duckdb");
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
            paths.filter(p -> p.getFileName().toString().contains("fill-replay-test-"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {}
    }

    @Test
    void replaysSinglePartiallyFilledEvent() throws Exception {
        // Seed one OrderPartiallyFilled via DuckDbEventStore
        Order order = createOrder("ORD-PART", "corr-part", "SBIN", 200, 150_00L);
        List<Trade> trades = List.of(
                new Trade("TRADE-1", "ORD-PART", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 150_00L, 1000L),
                new Trade("TRADE-2", "ORD-PART", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 151_00L, 1001L)
        );
        var seededEvent = new OrderPartiallyFilled(EventMetadata.correlated("corr-part", 1L), order, trades);
        eventStore.onEvent(seededEvent);

        // Set up a collecting EventBus
        List<OrderPartiallyFilled> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new FillCollectingEventBus(received, null);

        // Replay all fill events
        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var result = historicalRangeService.replayFillEvents(null, fromMs, toMs, eventBus);

        // Verify replay result
        assertTrue(result.isComplete(), "All fill events should be replayed successfully");
        assertEquals(1L, result.replayed());
        assertEquals(0L, result.failed());

        // Verify the replayed event
        assertEquals(1, received.size());
        OrderPartiallyFilled replayed = received.get(0);
        assertEquals("ORD-PART", replayed.order().orderId());
        assertEquals("corr-part", replayed.order().correlationId());
        assertEquals("SBIN", replayed.order().symbol());
        assertEquals(OrderStatus.PART_TRADED, replayed.order().status());
        assertEquals(200, replayed.order().quantity());
        // fill_events stores average fill price: (150_00 + 151_00) / 2
        assertEquals(150_50L, replayed.order().pricePaisa());
        assertEquals(200L, replayed.order().filledQuantity());
    }

    @Test
    void replaysSingleFullyFilledEvent() throws Exception {
        // Seed one OrderFullyFilled via DuckDbEventStore
        Order order = createOrder("ORD-FULL", "corr-full", "TCS", 100, 3_200_00L);
        List<Trade> trades = List.of(
                new Trade("TRADE-3", "ORD-FULL", "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 100, 3_200_00L, 2000L)
        );
        var seededEvent = new OrderFullyFilled(EventMetadata.correlated("corr-full", 1L), order, trades);
        eventStore.onEvent(seededEvent);

        List<OrderFullyFilled> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new FillCollectingEventBus(null, received);

        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var result = historicalRangeService.replayFillEvents(null, fromMs, toMs, eventBus);

        assertTrue(result.isComplete());
        assertEquals(1L, result.replayed());
        assertEquals(1, received.size());

        OrderFullyFilled replayed = received.get(0);
        assertEquals("ORD-FULL", replayed.order().orderId());
        assertEquals("corr-full", replayed.order().correlationId());
        assertEquals("TCS", replayed.order().symbol());
        assertEquals(OrderStatus.TRADED, replayed.order().status());
        assertEquals(100, replayed.order().quantity());
        assertEquals(3_200_00L, replayed.order().pricePaisa());
    }

    @Test
    void replaysMultipleFillEventsInIngestionOrder() throws Exception {
        // Seed three fill events in a known sequence
        eventStore.onEvent(new OrderPartiallyFilled(
                EventMetadata.correlated("corr-a", 1L),
                createOrder("ORD-P1", "corr-a", "SBIN", 200, 150_00L),
                List.of(new Trade("T1", "ORD-P1", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 200, 150_00L, 1000L))
        ));
        eventStore.onEvent(new OrderPartiallyFilled(
                EventMetadata.correlated("corr-b", 1L),
                createOrder("ORD-P2", "corr-b", "TCS", 50, 3_200_00L),
                List.of(new Trade("T2", "ORD-P2", "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 50, 3_200_00L, 2000L))
        ));
        eventStore.onEvent(new OrderFullyFilled(
                EventMetadata.correlated("corr-c", 1L),
                createOrder("ORD-F1", "corr-c", "RELIANCE", 300, 2_500_00L),
                List.of(new Trade("T3", "ORD-F1", "RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY, 300, 2_500_00L, 3000L))
        ));

        List<DomainEvent> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new AllDomainEventCollectingBus(received);

        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var result = historicalRangeService.replayFillEvents(null, fromMs, toMs, eventBus);

        assertTrue(result.isComplete());
        assertEquals(3L, result.replayed());
        assertEquals(3, received.size());

        // Verify chronological order (by ingestion time)
        DomainEvent first = received.get(0);
        DomainEvent second = received.get(1);
        DomainEvent third = received.get(2);

        assertInstanceOf(OrderPartiallyFilled.class, first);
        assertEquals("ORD-P1", ((OrderPartiallyFilled) first).order().orderId());

        assertInstanceOf(OrderPartiallyFilled.class, second);
        assertEquals("ORD-P2", ((OrderPartiallyFilled) second).order().orderId());

        assertInstanceOf(OrderFullyFilled.class, third);
        assertEquals("ORD-F1", ((OrderFullyFilled) third).order().orderId());

        // Verify symbols and prices
        assertEquals("SBIN", ((OrderPartiallyFilled) first).order().symbol());
        assertEquals(150_00L, ((OrderPartiallyFilled) first).order().pricePaisa());

        assertEquals("TCS", ((OrderPartiallyFilled) second).order().symbol());
        assertEquals(3_200_00L, ((OrderPartiallyFilled) second).order().pricePaisa());

        assertEquals("RELIANCE", ((OrderFullyFilled) third).order().symbol());
        assertEquals(2_500_00L, ((OrderFullyFilled) third).order().pricePaisa());
    }

    @Test
    void filtersBySymbol() throws Exception {
        // Seed fill events for two different symbols
        eventStore.onEvent(new OrderPartiallyFilled(
                EventMetadata.correlated("corr-sbin", 1L),
                createOrder("ORD-PS", "corr-sbin", "SBIN", 100, 150_00L),
                List.of(new Trade("TS1", "ORD-PS", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 150_00L, 1000L))
        ));
        eventStore.onEvent(new OrderPartiallyFilled(
                EventMetadata.correlated("corr-tcs", 1L),
                createOrder("ORD-PT", "corr-tcs", "TCS", 50, 3_200_00L),
                List.of(new Trade("TT1", "ORD-PT", "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 50, 3_200_00L, 2000L))
        ));

        // Replay only SBIN fill events
        List<DomainEvent> sbinReceived = new CopyOnWriteArrayList<>();
        EventBus sbinBus = new AllDomainEventCollectingBus(sbinReceived);

        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var sbinResult = historicalRangeService.replayFillEvents("SBIN", fromMs, toMs, sbinBus);

        assertEquals(1L, sbinResult.replayed());
        assertEquals(1, sbinReceived.size());
        assertEquals("SBIN", ((OrderPartiallyFilled) sbinReceived.get(0)).order().symbol());

        // Replay only TCS fill events
        List<DomainEvent> tcsReceived = new CopyOnWriteArrayList<>();
        EventBus tcsBus = new AllDomainEventCollectingBus(tcsReceived);

        var tcsResult = historicalRangeService.replayFillEvents("TCS", fromMs, toMs, tcsBus);

        assertEquals(1L, tcsResult.replayed());
        assertEquals(1, tcsReceived.size());
        assertEquals("TCS", ((OrderPartiallyFilled) tcsReceived.get(0)).order().symbol());
    }

    @Test
    void preservesFillCountAndTradeDistribution() throws Exception {
        // Seed an OrderPartiallyFilled with 3 fills totaling 50 quantity
        Order order = createOrder("ORD-3FILL", "corr-3fill", "HDFC", 50, 2_800_00L);
        List<Trade> trades = List.of(
                new Trade("TF1", "ORD-3FILL", "HDFC", ExchangeSegment.NSE_EQ, Side.BUY, 20, 2_800_00L, 1000L),
                new Trade("TF2", "ORD-3FILL", "HDFC", ExchangeSegment.NSE_EQ, Side.BUY, 20, 2_801_00L, 1001L),
                new Trade("TF3", "ORD-3FILL", "HDFC", ExchangeSegment.NSE_EQ, Side.BUY, 10, 2_802_00L, 1002L)
        );
        eventStore.onEvent(new OrderPartiallyFilled(
                EventMetadata.correlated("corr-3fill", 1L), order, trades
        ));

        List<OrderPartiallyFilled> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new FillCollectingEventBus(received, null);

        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var result = historicalRangeService.replayFillEvents(null, fromMs, toMs, eventBus);

        assertTrue(result.isComplete());
        assertEquals(1L, result.replayed());
        assertEquals(1, received.size());

        OrderPartiallyFilled replayed = received.get(0);
        List<Trade> replayedTrades = replayed.fills();

        // Trade distribution: perFillQty = 50/3 = 16, remainder = 2
        // Trade 0: 16 + 2 = 18, Trade 1: 16, Trade 2: 16
        assertEquals(3, replayedTrades.size(), "Should reconstruct 3 trades from fill_count=3");
        assertEquals(18L, replayedTrades.get(0).quantity(), "First trade gets remainder");
        assertEquals(16L, replayedTrades.get(1).quantity());
        assertEquals(16L, replayedTrades.get(2).quantity());

        // Sum of synthetic trades should equal original quantity
        long totalQty = replayedTrades.stream().mapToLong(Trade::quantity).sum();
        assertEquals(50L, totalQty);

        // Verify trade IDs encode the fill index
        assertTrue(replayedTrades.get(0).tradeId().endsWith("-0"));
        assertTrue(replayedTrades.get(1).tradeId().endsWith("-1"));
        assertTrue(replayedTrades.get(2).tradeId().endsWith("-2"));

        // Verify default metadata fields on reconstructed Order
        assertEquals(ExchangeSegment.UNKNOWN, replayed.order().exchangeSegment());
        assertEquals(Side.UNKNOWN, replayed.order().side());
        assertEquals(ProductType.INTRADAY, replayed.order().productType());
        assertEquals(OrderType.MARKET, replayed.order().orderType());
        assertEquals(0L, replayed.order().exchangeTimeMs());

        // Verify trades also use defaults
        assertEquals(ExchangeSegment.UNKNOWN, replayedTrades.get(0).exchangeSegment());
        assertEquals(Side.UNKNOWN, replayedTrades.get(0).side());
        assertEquals(0L, replayedTrades.get(0).exchangeTimeMs());
    }

    @Test
    void returnsEmptyResultWhenNoFillEventsInRange() throws Exception {
        List<DomainEvent> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new AllDomainEventCollectingBus(received);

        // No fill events seeded — empty DB
        var result = historicalRangeService.replayFillEvents(null, 0L, Long.MAX_VALUE, eventBus);

        assertTrue(result.isEmpty());
        assertEquals(0L, result.totalRead());
        assertEquals(0L, result.replayed());
        assertEquals(0L, result.failed());
        assertTrue(received.isEmpty());
    }

    @Test
    void onlyReplaysFillEventsWithinTimeRange() throws Exception {
        // Seed first fill event and capture its timestamp as the cutoff boundary
        eventStore.onEvent(new OrderPartiallyFilled(
                EventMetadata.correlated("corr-early", 1L),
                createOrder("ORD-EARLY", "corr-early", "SBIN", 100, 150_00L),
                List.of(new Trade("TE1", "ORD-EARLY", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 150_00L, 1000L))
        ));
        long midMs = INGEST_BASE_MS + (INGEST_STEP_MS / 2);
        eventStore.onEvent(new OrderPartiallyFilled(
                EventMetadata.correlated("corr-late", 1L),
                createOrder("ORD-LATE", "corr-late", "TCS", 50, 3_200_00L),
                List.of(new Trade("TL1", "ORD-LATE", "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 50, 3_200_00L, 2000L))
        ));

        // Replay with window [0, midMs) — should only catch the first event
        List<DomainEvent> earlyReceived = new CopyOnWriteArrayList<>();
        EventBus earlyBus = new AllDomainEventCollectingBus(earlyReceived);
        var earlyResult = historicalRangeService.replayFillEvents(null, 0L, midMs, earlyBus);

        assertEquals(1L, earlyResult.replayed());
        assertEquals(1, earlyReceived.size());
        assertEquals("ORD-EARLY",
                ((OrderPartiallyFilled) earlyReceived.get(0)).order().orderId());

        // Replay with window [midMs, now+60s] — should only catch the second event
        List<DomainEvent> lateReceived = new CopyOnWriteArrayList<>();
        EventBus lateBus = new AllDomainEventCollectingBus(lateReceived);
        var lateResult = historicalRangeService.replayFillEvents(
                null, midMs, System.currentTimeMillis() + 60_000L, lateBus);

        assertTrue(lateResult.isComplete());
        assertEquals(1L, lateResult.replayed());
        assertEquals(1, lateReceived.size());
        assertEquals("ORD-LATE",
                ((OrderPartiallyFilled) lateReceived.get(0)).order().orderId());
    }

    /**
     * Verifies that the DuckDbEventStore can re-persist replayed fill events.
     * This demonstrates the full cycle: seed → replay → store → verify.
     */
    @Test
    void partialFillUsesCorrectFilledQuantity() throws Exception {
        // Seed a true partial fill where filledQty < qty
        // This specifically verifies the Phase 4c fix: replayFillEvents must use
        // stored filled_quantity, not just qty, when reconstructing the Order.
        Order order = createOrderWithFilledQty(
                "ORD-PARTIAL-FILL", "corr-partial", "SBIN",
                500L /* total qty */, 100L /* filled qty */, 150_00L
        );
        List<Trade> trades = List.of(
                new Trade("TRADE-P1", "ORD-PARTIAL-FILL", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 150_00L, 1000L)
        );
        var seededEvent = new OrderPartiallyFilled(
                EventMetadata.correlated("corr-partial", 1L), order, trades
        );
        eventStore.onEvent(seededEvent);

        List<OrderPartiallyFilled> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new FillCollectingEventBus(received, null);

        long fromMs = 0L;
        long toMs = System.currentTimeMillis() + 60_000L;
        var result = historicalRangeService.replayFillEvents(null, fromMs, toMs, eventBus);

        assertTrue(result.isComplete());
        assertEquals(1L, result.replayed());
        assertEquals(1, received.size());

        OrderPartiallyFilled replayed = received.get(0);
        assertEquals("ORD-PARTIAL-FILL", replayed.order().orderId());

        // CRITICAL: filledQuantity must be 100 (the actual filled quantity),
        // NOT 500 (total quantity). This catches the Phase 4c bug where
        // replayFillEvents used qty instead of filled_quantity.
        assertEquals(500L, replayed.order().quantity(),
                "Total quantity should be preserved");
        assertEquals(100L, replayed.order().filledQuantity(),
                "Filled quantity must be the stored filled_quantity, not total qty");

        // Verify persisted metadata fields survive the round-trip
        assertEquals(ExchangeSegment.NSE_EQ, replayed.order().exchangeSegment(),
                "Exchange segment persisted in fill_events should be reconstructed");
        assertEquals(Side.BUY, replayed.order().side(),
                "Side persisted in fill_events should be reconstructed");
        assertEquals(OrderStatus.PART_TRADED, replayed.order().status(),
                "Status should be preserved");

        // ProductType, OrderType, and exchangeTimeMs are NOT persisted in fill_events
        // and use hardcoded defaults (INTRADAY, MARKET, 0) during reconstruction.
        // This is intentional; only exchange_segment and side were added in the migration.
        assertEquals(ProductType.INTRADAY, replayed.order().productType());
        assertEquals(OrderType.MARKET, replayed.order().orderType());
        assertEquals(0L, replayed.order().exchangeTimeMs());

        // Verify reconstructed trades also carry persisted metadata
        assertEquals(ExchangeSegment.NSE_EQ, replayed.fills().get(0).exchangeSegment());
        assertEquals(Side.BUY, replayed.fills().get(0).side());
    }

    @Test
    void replayedEventsCanBeStoredByIdempotentSubscriber() throws Exception {
        // Seed a partially filled event
        eventStore.onEvent(new OrderPartiallyFilled(
                EventMetadata.correlated("corr-cycle", 1L),
                createOrder("ORD-CYCLE", "corr-cycle", "SBIN", 100, 150_00L),
                List.of(new Trade("TC1", "ORD-CYCLE", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 150_00L, 1000L))
        ));

        // Replay through an EventBus that also feeds DuckDbEventStore (like live does)
        List<DomainEvent> received = new CopyOnWriteArrayList<>();
        EventBus eventBus = new EventBus() {
            @Override
            public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {}

            @Override
            public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {}

            @Override
            public void start() {}

            @Override
            public void stop() {}

            @Override
            public void publish(DomainEvent event) {
                if (event instanceof OrderPartiallyFilled || event instanceof OrderFullyFilled) {
                    received.add(event);
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
        var result = historicalRangeService.replayFillEvents(null, fromMs, toMs, eventBus);

        assertTrue(result.isComplete());
        assertEquals(1L, result.replayed());

        // Re-query the fill_events table — replayed event gets re-persisted by DuckDbEventStore
        // via insertFillEvent, producing a second row (the original + the replayed copy).
        var reQueriedFillEvents = historicalRangeService.queryFillEvents("SBIN", fromMs, toMs);
        assertEquals(2, reQueriedFillEvents.size(),
                "Should have the original + replayed fill event rows");
    }

    // ── Helpers ──

    private static Order createOrder(String orderId, String correlationId, String symbol,
                                     long quantity, long pricePaisa) {
        return createOrder(orderId, correlationId, symbol, quantity, quantity, pricePaisa);
    }

    private static Order createOrderWithFilledQty(String orderId, String correlationId, String symbol,
                                                   long quantity, long filledQuantity, long pricePaisa) {
        return new Order(
                orderId, correlationId, symbol,
                ExchangeSegment.NSE_EQ, Side.BUY, ProductType.INTRADAY, OrderType.LIMIT,
                OrderStatus.PART_TRADED, quantity, filledQuantity, pricePaisa, 0L, 0L, ""
        );
    }

    private static Order createOrder(String orderId, String correlationId, String symbol,
                                     long quantity, long filledQuantity, long pricePaisa) {
        return new Order(
                orderId, correlationId, symbol,
                ExchangeSegment.UNKNOWN, Side.UNKNOWN, ProductType.INTRADAY, OrderType.MARKET,
                OrderStatus.OPEN, quantity, filledQuantity, pricePaisa, 0L, 0L, ""
        );
    }

    /**
     * Minimal EventBus that collects either {@link OrderPartiallyFilled} or
     * {@link OrderFullyFilled} events (the non-null collector receives them).
     */
    private static final class FillCollectingEventBus implements EventBus {
        private final List<OrderPartiallyFilled> partialSink;
        private final List<OrderFullyFilled> fullSink;

        FillCollectingEventBus(List<OrderPartiallyFilled> partialSink,
                               List<OrderFullyFilled> fullSink) {
            this.partialSink = partialSink;
            this.fullSink = fullSink;
        }

        @Override
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {}

        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {}

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void publish(DomainEvent event) {
            if (event instanceof OrderPartiallyFilled pf && partialSink != null) {
                partialSink.add(pf);
            } else if (event instanceof OrderFullyFilled ff && fullSink != null) {
                fullSink.add(ff);
            }
        }
    }

    /**
     * Minimal EventBus that collects all {@link DomainEvent} instances for generic inspection.
     */
    private static final class AllDomainEventCollectingBus implements EventBus {
        private final List<DomainEvent> sink;

        AllDomainEventCollectingBus(List<DomainEvent> sink) {
            this.sink = sink;
        }

        @Override
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {}

        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {}

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void publish(DomainEvent event) {
            sink.add(event);
        }
    }
}
