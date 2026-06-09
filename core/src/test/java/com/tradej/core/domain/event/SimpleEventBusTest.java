package com.tradej.core.domain.event;

import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.testing.EventFactories;
import com.tradej.core.testing.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SimpleEventBusTest {

    private SimpleEventBus bus;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        bus = new SimpleEventBus();
        clock = TestClock.fixed(Instant.parse("2026-06-08T10:00:00Z"));
    }

    private MarketTickEvent tick(String symbol, long ltp) {
        return EventFactories.tick(symbol, ltp, clock);
    }

    @Test
    void publishDeliversToExactTypeSubscriber() {
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(MarketTickEvent.class, received::add);

        MarketTickEvent event = tick("RELIANCE", 100L);
        bus.publish(event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
    }

    @Test
    void publishDeliversToCatchAllSubscriber() {
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(DomainEvent.class, received::add);

        MarketTickEvent event = tick("RELIANCE", 100L);
        bus.publish(event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
    }

    @Test
    void publishDeliversToBothExactAndCatchAll() {
        List<DomainEvent> exact = new ArrayList<>();
        List<DomainEvent> catchAll = new ArrayList<>();
        bus.subscribe(MarketTickEvent.class, exact::add);
        bus.subscribe(DomainEvent.class, catchAll::add);

        bus.publish(tick("RELIANCE", 100L));

        assertEquals(1, exact.size());
        assertEquals(1, catchAll.size());
    }

    @Test
    void publishIgnoresNull() {
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(DomainEvent.class, received::add);

        bus.publish(null);

        assertTrue(received.isEmpty());
    }

    @Test
    void unsubscribeRemovesHandler() {
        List<DomainEvent> received = new ArrayList<>();
        DomainEventHandler<MarketTickEvent> handler = received::add;
        bus.subscribe(MarketTickEvent.class, handler);
        bus.unsubscribe(MarketTickEvent.class, handler);

        bus.publish(tick("RELIANCE", 100L));

        assertTrue(received.isEmpty());
    }

    @Test
    void failingHandlerDoesNotBlockOthers() {
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(MarketTickEvent.class, event -> { throw new RuntimeException("boom"); });
        bus.subscribe(MarketTickEvent.class, received::add);

        bus.publish(tick("RELIANCE", 100L));

        assertEquals(1, received.size(), "Second handler should still receive event despite first handler failure");
    }

    @Test
    void startAndStopLifecycle() {
        assertFalse(bus.isStarted());
        bus.start();
        assertTrue(bus.isStarted());
        bus.stop();
        assertFalse(bus.isStarted());
    }

    @Test
    void subscriberCountReflectsRegistrations() {
        assertEquals(0, bus.subscriberCount());
        bus.subscribe(MarketTickEvent.class, e -> {});
        bus.subscribe(MarketTickEvent.class, e -> {});
        bus.subscribe(DomainEvent.class, e -> {});
        assertEquals(3, bus.subscriberCount());
        assertEquals(2, bus.eventTypeCount());
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
        assertEquals(threadCount * eventsPerThread, received.size());
    }
}
