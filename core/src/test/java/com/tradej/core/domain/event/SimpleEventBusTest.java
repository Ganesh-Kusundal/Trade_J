package com.tradej.core.domain.event;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.testing.EventBusContractTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Concrete contract test for {@link SimpleEventBus}.
 *
 * <p>All {@link EventBusContractTest contract tests} are inherited and run
 * automatically. Only SimpleEventBus-specific tests are defined here.
 */
@Tag("unit")
class SimpleEventBusTest extends EventBusContractTest {

    @Override
    protected EventBus createEventBus() {
        return new SimpleEventBus();
    }

    @Test
    void subscriberCountReflectsRegistrations() {
        SimpleEventBus simpleBus = (SimpleEventBus) bus;
        assertEquals(0, simpleBus.subscriberCount());
        simpleBus.subscribe(MarketTickEvent.class, e -> {});
        simpleBus.subscribe(MarketTickEvent.class, e -> {});
        simpleBus.subscribe(DomainEvent.class, e -> {});
        assertEquals(3, simpleBus.subscriberCount());
        assertEquals(2, simpleBus.eventTypeCount());
    }
}
