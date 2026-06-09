package com.tradej.app.config;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.testing.EventFactories;
import com.tradej.core.testing.TestClock;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.disruptor.config.BrokerScopedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class EventBusConfigurationTest {

    private EventBusConfiguration config;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        config = new EventBusConfiguration();
        clock = TestClock.fixed(Instant.parse("2026-06-08T10:00:00Z"));
    }

    @Test
    void primaryEventBusIsSimpleEventBus() {
        EventBus bus = config.eventBus();

        assertInstanceOf(SimpleEventBus.class, bus,
                "Primary EventBus must be SimpleEventBus, not NoOpEventBus");
    }

    @Test
    void primaryEventBusDeliversEvents() {
        EventBus bus = config.eventBus();
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(MarketTickEvent.class, received::add);

        bus.publish(EventFactories.tick("RELIANCE", 250000L, clock));

        assertEquals(1, received.size(), "Primary EventBus must deliver events to subscribers");
    }

    @Test
    void brokerScopedBusDelegatesToPrimary() {
        EventBus primary = config.eventBus();
        BrokerScopedEventBus dhanBus = config.dhanEventBus(primary);

        List<DomainEvent> received = new ArrayList<>();
        primary.subscribe(MarketTickEvent.class, received::add);

        dhanBus.publish(EventFactories.tick("RELIANCE", 250000L, clock));

        assertEquals(1, received.size(), "Broker-scoped bus must delegate to primary bus");
    }

    @Test
    void brokerScopedBusHasCorrectBrokerId() {
        EventBus primary = config.eventBus();

        assertEquals("dhan", config.dhanEventBus(primary).brokerId());
        assertEquals("upstox", config.upstoxEventBus(primary).brokerId());
        assertEquals("icici", config.iciciEventBus(primary).brokerId());
    }

    @Test
    void metricsReturnsRealCountsForSimpleBus() {
        EventBus primary = config.eventBus();
        primary.subscribe(MarketTickEvent.class, e -> {});
        primary.subscribe(DomainEvent.class, e -> {});

        DisruptorBusMetrics metrics = config.disruptorBusMetrics(primary);

        assertInstanceOf(SimpleBusMetrics.class, metrics,
                "Metrics for SimpleEventBus should be SimpleBusMetrics");
        assertEquals(2, metrics.subscriberCount());
    }

    @Test
    void metricsFallbackForNonSimpleBus() {
        EventBus customBus = new EventBus() {
            @Override public <T extends DomainEvent> void subscribe(Class<T> t, com.tradej.core.domain.port.DomainEventHandler<T> h) {}
            @Override public <T extends DomainEvent> void unsubscribe(Class<T> t, com.tradej.core.domain.port.DomainEventHandler<T> h) {}
            @Override public void publish(DomainEvent e) {}
            @Override public void start() {}
            @Override public void stop() {}
        };

        DisruptorBusMetrics metrics = config.disruptorBusMetrics(customBus);

        assertInstanceOf(NoOpBusMetrics.class, metrics,
                "Non-SimpleEventBus should get NoOpBusMetrics fallback");
        assertEquals(0, metrics.subscriberCount());
    }
}
