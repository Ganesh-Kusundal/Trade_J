package com.tradej.disruptor.config;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for R19: Per-Broker Event Bus Isolation.
 * Verifies that broker-scoped buses operate independently.
 */
@Tag("unit")
class PerBrokerEventBusIsolationTest {

    @Test
    void brokerScopedBus_delegatesPublish() {
        StubEventBus stub = new StubEventBus();
        BrokerScopedEventBus bus = new BrokerScopedEventBus(stub, "dhan");

        bus.publish(new StubEvent());

        assertEquals(1, stub.publishedCount.get());
    }

    @Test
    void brokerScopedBus_hasBrokerId() {
        StubEventBus stub = new StubEventBus();
        BrokerScopedEventBus dhanBus = new BrokerScopedEventBus(stub, "dhan");
        BrokerScopedEventBus upstoxBus = new BrokerScopedEventBus(stub, "upstox");

        assertEquals("dhan", dhanBus.brokerId());
        assertEquals("upstox", upstoxBus.brokerId());
    }

    @Test
    void multipleBuses_independentSubscriptions() {
        StubEventBus dhanDelegate = new StubEventBus();
        StubEventBus upstoxDelegate = new StubEventBus();
        BrokerScopedEventBus dhanBus = new BrokerScopedEventBus(dhanDelegate, "dhan");
        BrokerScopedEventBus upstoxBus = new BrokerScopedEventBus(upstoxDelegate, "upstox");

        AtomicInteger dhanCount = new AtomicInteger();
        AtomicInteger upstoxCount = new AtomicInteger();

        dhanBus.subscribe(StubEvent.class, event -> dhanCount.incrementAndGet());
        upstoxBus.subscribe(StubEvent.class, event -> upstoxCount.incrementAndGet());

        // Publish to dhan only
        dhanBus.publish(new StubEvent());

        assertEquals(1, dhanDelegate.subscriberCount);
        assertEquals(1, upstoxDelegate.subscriberCount);
        // Each bus has its own subscription — they don't share
    }

    @Test
    void multipleBuses_independentPublishBatch() {
        StubEventBus dhanDelegate = new StubEventBus();
        StubEventBus upstoxDelegate = new StubEventBus();
        BrokerScopedEventBus dhanBus = new BrokerScopedEventBus(dhanDelegate, "dhan");
        BrokerScopedEventBus upstoxBus = new BrokerScopedEventBus(upstoxDelegate, "upstox");

        dhanBus.publishBatch(List.of(new StubEvent(), new StubEvent(), new StubEvent()));

        assertEquals(3, dhanDelegate.publishedCount.get());
        assertEquals(0, upstoxDelegate.publishedCount.get(), "Upstox bus should not receive Dhan events");
    }

    @Test
    void multipleBuses_independentLifecycle() {
        StubEventBus dhanDelegate = new StubEventBus();
        StubEventBus upstoxDelegate = new StubEventBus();
        BrokerScopedEventBus dhanBus = new BrokerScopedEventBus(dhanDelegate, "dhan");
        BrokerScopedEventBus upstoxBus = new BrokerScopedEventBus(upstoxDelegate, "upstox");

        dhanBus.start();
        assertTrue(dhanDelegate.started);
        assertFalse(upstoxDelegate.started, "Upstox bus should not start when Dhan starts");

        upstoxBus.start();
        assertTrue(upstoxDelegate.started);
    }

    // Stubs

    static class StubEventBus implements EventBus {
        final AtomicInteger publishedCount = new AtomicInteger();
        int subscriberCount = 0;
        boolean started = false;

        @Override
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            subscriberCount++;
        }

        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            subscriberCount--;
        }

        @Override
        public void publish(DomainEvent event) {
            publishedCount.incrementAndGet();
        }

        @Override
        public void publishBatch(List<? extends DomainEvent> events) {
            publishedCount.addAndGet(events.size());
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            started = false;
        }
    }

    record StubEvent() implements DomainEvent {
        @Override
        public void accept(com.tradej.core.domain.event.DomainEventVisitor visitor) {}

        @Override
        public com.tradej.core.domain.event.EventMetadata metadata() {
            return null;
        }
    }
}
