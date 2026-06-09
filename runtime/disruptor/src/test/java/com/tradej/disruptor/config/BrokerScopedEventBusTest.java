package com.tradej.disruptor.config;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.testing.EventFactories;
import com.tradej.core.testing.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BrokerScopedEventBusTest {

    private SimpleEventBus delegate;
    private BrokerScopedEventBus scopedBus;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        delegate = new SimpleEventBus();
        scopedBus = new BrokerScopedEventBus(delegate, "dhan");
        clock = TestClock.fixed(Instant.parse("2026-06-08T10:00:00Z"));
    }

    @Test
    void publishDelegatesToUnderlyingBus() {
        List<DomainEvent> received = new ArrayList<>();
        delegate.subscribe(MarketTickEvent.class, received::add);

        scopedBus.publish(EventFactories.tick("RELIANCE", 100L, clock));

        assertEquals(1, received.size());
    }

    @Test
    void subscribeDelegatesToUnderlyingBus() {
        List<DomainEvent> received = new ArrayList<>();
        scopedBus.subscribe(MarketTickEvent.class, received::add);

        delegate.publish(EventFactories.tick("RELIANCE", 100L, clock));

        assertEquals(1, received.size(), "Handler registered via scoped bus should receive events published to delegate");
    }

    @Test
    void unsubscribeDelegatesToUnderlyingBus() {
        List<DomainEvent> received = new ArrayList<>();
        DomainEventHandler<MarketTickEvent> handler = received::add;
        scopedBus.subscribe(MarketTickEvent.class, handler);
        scopedBus.unsubscribe(MarketTickEvent.class, handler);

        scopedBus.publish(EventFactories.tick("RELIANCE", 100L, clock));

        assertTrue(received.isEmpty());
    }

    @Test
    void brokerIdIsPreserved() {
        assertEquals("dhan", scopedBus.brokerId());
    }

    @Test
    void delegateIsAccessible() {
        assertSame(delegate, scopedBus.delegate());
    }

    @Test
    void startAndStopDelegate() {
        scopedBus.start();
        assertTrue(delegate.isStarted());
        scopedBus.stop();
        assertFalse(delegate.isStarted());
    }

    @Test
    void publishBatchDelegatesAllEvents() {
        List<DomainEvent> received = new ArrayList<>();
        delegate.subscribe(DomainEvent.class, received::add);

        scopedBus.publishBatch(List.of(
                EventFactories.tick("A", 100L, clock),
                EventFactories.tick("B", 200L, clock)
        ));

        assertEquals(2, received.size());
    }
}
