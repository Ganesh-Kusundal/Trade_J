package com.tradej.core.testing;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectingEventBusTest {

    private CollectingEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new CollectingEventBus();
    }

    @Test
    void publishedEventsAreCaptured() {
        var event = createTick("AAPL", 100L);
        bus.publish(event);
        assertEquals(1, bus.totalCount());
        assertEquals(event, bus.allEvents().getFirst());
    }

    @Test
    void eventsOfReturnsCorrectType() {
        bus.publish(createTick("AAPL", 100L));
        bus.publish(createCandle("AAPL", 200L));
        bus.publish(createTick("AAPL", 101L));

        assertEquals(2, bus.eventsOf(MarketTickEvent.class).size());
        assertEquals(1, bus.eventsOf(CandleClosed.class).size());
    }

    @Test
    void firstEventOfReturnsFirst() {
        var tick1 = createTick("AAPL", 100L);
        var tick2 = createTick("AAPL", 101L);
        bus.publish(tick1);
        bus.publish(tick2);
        assertEquals(tick1, bus.firstEventOf(MarketTickEvent.class));
    }

    @Test
    void lastEventOfReturnsLast() {
        var tick1 = createTick("AAPL", 100L);
        var tick2 = createTick("AAPL", 101L);
        bus.publish(tick1);
        bus.publish(tick2);
        assertEquals(tick2, bus.lastEventOf(MarketTickEvent.class));
    }

    @Test
    void countOfReturnsCorrectCount() {
        bus.publish(createTick("AAPL", 100L));
        bus.publish(createTick("AAPL", 101L));
        bus.publish(createCandle("AAPL", 200L));
        assertEquals(2, bus.countOf(MarketTickEvent.class));
        assertEquals(1, bus.countOf(CandleClosed.class));
    }

    @Test
    void assertNoEventsOfPassesWhenEmpty() {
        bus.assertNoEventsOf(MarketTickEvent.class);
    }

    @Test
    void assertNoEventsOfFailsWhenEventsExist() {
        bus.publish(createTick("AAPL", 100L));
        try {
            bus.assertNoEventsOf(MarketTickEvent.class);
            throw new AssertionError("Should have thrown");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("Expected no events"));
        }
    }

    @Test
    void assertHasEventOfPassesWhenEventsExist() {
        bus.publish(createTick("AAPL", 100L));
        bus.assertHasEventOf(MarketTickEvent.class);
    }

    @Test
    void assertHasEventOfFailsWhenEmpty() {
        try {
            bus.assertHasEventOf(MarketTickEvent.class);
            throw new AssertionError("Should have thrown");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("Expected at least one"));
        }
    }

    @Test
    void clearRemovesAllEvents() {
        bus.publish(createTick("AAPL", 100L));
        bus.publish(createCandle("AAPL", 200L));
        bus.clear();
        assertEquals(0, bus.totalCount());
    }

    @Test
    void startStopLifecycle() {
        assertFalse(bus.isStarted());
        bus.start();
        assertTrue(bus.isStarted());
        bus.stop();
        assertFalse(bus.isStarted());
    }

    @Test
    void eventsOfReturnsEmptyListForUnknownType() {
        List<SignalGenerated> signals = bus.eventsOf(SignalGenerated.class);
        assertTrue(signals.isEmpty());
    }

    // -- helpers --

    private static MarketTickEvent createTick(String symbol, long ltp) {
        return new MarketTickEvent(
                EventMetadata.root(),
                0L,
                symbol,
                ExchangeSegment.NSE_EQ,
                FeedMode.TICKER,
                ltp,
                1L,
                100L,
                System.currentTimeMillis(),
                Optional.empty(), 0L, 0L
        );
    }

    private static CandleClosed createCandle(String symbol, long close) {
        return new CandleClosed(
                EventMetadata.root(),
                new Candle(
                        symbol,
                        "5m",
                        1000L,
                        1000L + 300_000L,
                        100L,
                        close,
                        95L,
                        close,
                        1000L,
                        true
                )
        );
    }
}
