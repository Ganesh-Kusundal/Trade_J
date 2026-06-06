package com.tradej.broker.core.depth;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderBookEngineTest {

    private static DepthUpdateEvent depthEvent(String symbol, ExchangeSegment segment,
                                                List<DepthLevel> bids, List<DepthLevel> asks) {
        return new DepthUpdateEvent(
                new EventMetadata("test", System.currentTimeMillis(), 0, 0, "", 1),
                symbol, segment, bids, asks, bids.size(), System.currentTimeMillis());
    }

    @Test
    void onDepthUpdateCreatesBook() {
        OrderBookEngine engine = new OrderBookEngine();
        engine.onDepthUpdate(depthEvent("RELIANCE", ExchangeSegment.NSE_EQ,
                List.of(new DepthLevel(250000, 100, 1)),
                List.of(new DepthLevel(250100, 50, 1))));
        assertNotNull(engine.getBook("RELIANCE", ExchangeSegment.NSE_EQ));
        assertEquals(1, engine.bookCount());
    }

    @Test
    void perSymbolIsolation() {
        OrderBookEngine engine = new OrderBookEngine();
        engine.onDepthUpdate(depthEvent("RELIANCE", ExchangeSegment.NSE_EQ,
                List.of(new DepthLevel(250000, 100, 1)), List.of()));
        engine.onDepthUpdate(depthEvent("TCS", ExchangeSegment.NSE_EQ,
                List.of(new DepthLevel(380000, 50, 1)), List.of()));
        assertEquals(2, engine.bookCount());
        assertEquals(250000, engine.getBook("RELIANCE", ExchangeSegment.NSE_EQ).bestBidPaisa());
        assertEquals(380000, engine.getBook("TCS", ExchangeSegment.NSE_EQ).bestBidPaisa());
    }

    @Test
    void getBookReturnsNullForUnknown() {
        OrderBookEngine engine = new OrderBookEngine();
        assertNull(engine.getBook("UNKNOWN", ExchangeSegment.NSE_EQ));
    }

    @Test
    void listenerNotifiedOnUpdate() {
        OrderBookEngine engine = new OrderBookEngine();
        AtomicInteger callCount = new AtomicInteger();
        engine.addListener(event -> callCount.incrementAndGet());
        engine.onDepthUpdate(depthEvent("RELIANCE", ExchangeSegment.NSE_EQ,
                List.of(new DepthLevel(250000, 100, 1)), List.of()));
        assertEquals(1, callCount.get());
    }

    @Test
    void listenerFailureDoesNotBlockOthers() {
        OrderBookEngine engine = new OrderBookEngine();
        AtomicInteger secondListenerCalls = new AtomicInteger();
        engine.addListener(event -> { throw new RuntimeException("fail"); });
        engine.addListener(event -> secondListenerCalls.incrementAndGet());
        engine.onDepthUpdate(depthEvent("RELIANCE", ExchangeSegment.NSE_EQ,
                List.of(new DepthLevel(250000, 100, 1)), List.of()));
        assertEquals(1, secondListenerCalls.get());
    }
}
