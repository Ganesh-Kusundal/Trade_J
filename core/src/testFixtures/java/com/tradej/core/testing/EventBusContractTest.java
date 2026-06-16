package com.tradej.core.testing;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract contract test that every {@link EventBus} implementation must pass.
 *
 * <p>Extend this class and implement {@link #createEventBus()} to verify that
 * a concrete EventBus satisfies the required contract.
 *
 * <pre>{@code
 * class MyEventBusTest extends EventBusContractTest {
 *     private MyEventBus bus;
 *
 *     {@literal @}Override
 *     protected EventBus createEventBus() {
 *         return new MyEventBus(...);
 *     }
 * }
 * }</pre>
 */
public abstract class EventBusContractTest {

    private final TestClock clock = TestClock.fixed(java.time.Instant.parse("2026-06-08T10:00:00Z"));

    protected EventBus bus;

    /** Subclasses must return a fresh, started instance of their EventBus. */
    protected abstract EventBus createEventBus();

    @BeforeEach
    void setUp() {
        bus = createEventBus();
        bus.start();
    }

    @AfterEach
    void tearDown() {
        if (bus != null) {
            bus.stop();
        }
    }

    // -- helpers --

    private MarketTickEvent tick(String symbol, long ltp) {
        return EventFactories.tick(symbol, ltp, clock);
    }

    private CandleClosed candle(String symbol, long close) {
        return EventFactories.candleClosed(symbol, close, clock);
    }

    // -- contract tests --

    @Test
    void publishDeliversToExactTypeSubscriber() {
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(MarketTickEvent.class, received::add);

        MarketTickEvent event = tick("RELIANCE", 100L);
        bus.publish(event);

        assertEquals(1, received.size(), "Exact-type subscriber should receive the event");
        assertSame(event, received.get(0), "Subscriber should receive the exact same event instance");
    }

    @Test
    void publishDeliversToCatchAllSubscriber() {
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(DomainEvent.class, received::add);

        MarketTickEvent event = tick("RELIANCE", 100L);
        bus.publish(event);

        assertEquals(1, received.size(), "Catch-all subscriber should receive the event");
        assertSame(event, received.get(0));
    }

    @Test
    void publishDeliversToBothExactAndCatchAll() {
        List<DomainEvent> exact = new ArrayList<>();
        List<DomainEvent> catchAll = new ArrayList<>();
        bus.subscribe(MarketTickEvent.class, exact::add);
        bus.subscribe(DomainEvent.class, catchAll::add);

        bus.publish(tick("RELIANCE", 100L));

        assertEquals(1, exact.size(), "Exact subscriber should receive the event");
        assertEquals(1, catchAll.size(), "Catch-all subscriber should receive the event");
    }

    @Test
    void publishOnlyDeliversToMatchingSubscribers() {
        List<DomainEvent> ticks = new ArrayList<>();
        List<DomainEvent> candles = new ArrayList<>();
        bus.subscribe(MarketTickEvent.class, ticks::add);
        bus.subscribe(CandleClosed.class, candles::add);

        bus.publish(tick("RELIANCE", 100L));
        bus.publish(candle("RELIANCE", 200L));

        assertEquals(1, ticks.size(), "Only one tick should be received");
        assertEquals(1, candles.size(), "Only one candle should be received");
    }

    @Test
    void publishNullIsSilentlyIgnored() {
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(DomainEvent.class, received::add);

        bus.publish(null);

        assertTrue(received.isEmpty(), "Publishing null should not trigger any subscriber");
    }

    @Test
    void unsubscribeRemovesHandler() {
        List<DomainEvent> received = new ArrayList<>();
        DomainEventHandler<MarketTickEvent> handler = received::add;
        bus.subscribe(MarketTickEvent.class, handler);
        bus.unsubscribe(MarketTickEvent.class, handler);

        bus.publish(tick("RELIANCE", 100L));

        assertTrue(received.isEmpty(), "Unsubscribed handler should not receive events");
    }

    @Test
    void unsubscribeOfUnregisteredHandlerDoesNotThrow() {
        DomainEventHandler<MarketTickEvent> handler = e -> {};
        // Should not throw even though handler was never subscribed
        bus.unsubscribe(MarketTickEvent.class, handler);
    }

    @Test
    void failingHandlerDoesNotBlockOtherHandlers() {
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(MarketTickEvent.class, event -> { throw new RuntimeException("intentional failure"); });
        bus.subscribe(MarketTickEvent.class, received::add);

        bus.publish(tick("RELIANCE", 100L));

        assertEquals(1, received.size(),
                "Second handler should still receive event despite first handler throwing");
    }

    @Test
    void publishBatchDeliversAllEvents() {
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(DomainEvent.class, received::add);

        var event1 = tick("RELIANCE", 100L);
        var event2 = candle("RELIANCE", 200L);
        var event3 = tick("TCS", 300L);
        bus.publishBatch(List.of(event1, event2, event3));

        assertEquals(3, received.size());
        assertSame(event1, received.get(0));
        assertSame(event2, received.get(1));
        assertSame(event3, received.get(2));
    }

    @Test
    void eventOrderIsPreserved() {
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(DomainEvent.class, received::add);

        var tick1 = tick("A", 10L);
        var tick2 = tick("A", 20L);
        var tick3 = tick("A", 30L);
        bus.publish(tick1);
        bus.publish(tick2);
        bus.publish(tick3);

        assertEquals(3, received.size());
        assertSame(tick1, received.get(0), "First published event should be first in order");
        assertSame(tick2, received.get(1), "Second published event should be second in order");
        assertSame(tick3, received.get(2), "Third published event should be third in order");
    }

    @Test
    void multipleSubscribersForSameEventTypeAllReceiveEvent() {
        List<DomainEvent> received1 = new ArrayList<>();
        List<DomainEvent> received2 = new ArrayList<>();
        List<DomainEvent> received3 = new ArrayList<>();
        bus.subscribe(MarketTickEvent.class, received1::add);
        bus.subscribe(MarketTickEvent.class, received2::add);
        bus.subscribe(MarketTickEvent.class, received3::add);

        bus.publish(tick("RELIANCE", 100L));

        assertEquals(1, received1.size());
        assertEquals(1, received2.size());
        assertEquals(1, received3.size());
    }

    @Test
    void concurrentPublishDoesNotLoseEvents() throws Exception {
        CopyOnWriteArrayList<DomainEvent> received = new CopyOnWriteArrayList<>();
        bus.subscribe(DomainEvent.class, received::add);

        int threadCount = 8;
        int eventsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < eventsPerThread; i++) {
                        bus.publish(tick("SYM", (long) i));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        // Allow async implementations to drain pending events before counting.
        drainPendingEvents();
        assertEquals(threadCount * eventsPerThread, received.size(),
                "All concurrently published events should be received");
    }

    /**
     * Hook for async EventBus implementations to drain pending events before
     * assertions. The default is a no-op (synchronous buses have nothing to drain).
     * Subclasses with async dispatch (e.g., ring-buffer-based) should override
     * to await all queued events before letting the count assertion run.
     */
    protected void drainPendingEvents() throws InterruptedException {
        // default: no-op for synchronous buses
    }

    @Test
    void handlerCanBeRegisteredAfterStart() {
        bus.start();
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(MarketTickEvent.class, received::add);

        bus.publish(tick("RELIANCE", 100L));

        assertEquals(1, received.size(),
                "Handlers registered after start should still receive events");
    }

    @Test
    void startAndStopLifecycle() {
        bus.stop();
        bus.start();
        // After restart, publishing should work
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(DomainEvent.class, received::add);
        bus.publish(tick("RELIANCE", 100L));
        assertEquals(1, received.size(), "EventBus should work after stop + start cycle");
    }
}
