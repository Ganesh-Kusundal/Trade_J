package com.tradej.disruptor;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.tracing.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that TracingEventBus propagates correlation IDs from event metadata
 * to TraceContext before handler dispatch, and clears it after.
 */
@Tag("unit")
class TracingEventBusTest {

    private RecordingEventBus delegate;
    private TracingEventBus tracingBus;

    @BeforeEach
    void setUp() {
        delegate = new RecordingEventBus();
        tracingBus = new TracingEventBus(delegate);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("Handler receives TraceContext correlation ID from event metadata")
    void handlerReceivesCorrelationId() {
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();

        tracingBus.subscribe(MarketTickEvent.class, event -> {
            capturedCorrelationId.set(TraceContext.getCorrelationId());
        });

        EventMetadata meta = new EventMetadata("evt-1", System.currentTimeMillis(),
                System.nanoTime(), 1L, "corr-abc-123", 1);
        MarketTickEvent tick = new MarketTickEvent(meta, 1L, "RELIANCE",
                ExchangeSegment.NSE_EQ, FeedMode.TICKER, 250000L, 100L, 50000L,
                System.currentTimeMillis(), Optional.empty(), 5000L, 5000L);

        tracingBus.publish(tick);

        assertEquals("corr-abc-123", capturedCorrelationId.get(),
                "Handler should see correlation ID from event metadata");
    }

    @Test
    @DisplayName("TraceContext is cleared after handler dispatch")
    void traceContextClearedAfterDispatch() {
        tracingBus.subscribe(MarketTickEvent.class, event -> {
            // handler runs with TraceContext set
        });

        EventMetadata meta = new EventMetadata("evt-2", System.currentTimeMillis(),
                System.nanoTime(), 2L, "corr-xyz", 1);
        MarketTickEvent tick = new MarketTickEvent(meta, 2L, "TCS",
                ExchangeSegment.NSE_EQ, FeedMode.TICKER, 350000L, 50L, 25000L,
                System.currentTimeMillis(), Optional.empty(), 3000L, 3000L);

        tracingBus.publish(tick);

        assertNull(TraceContext.getCorrelationId(),
                "TraceContext should be cleared after handler dispatch");
    }

    @Test
    @DisplayName("Events without correlation ID do not set TraceContext")
    void nullCorrelationIdDoesNotSetContext() {
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>("initial");

        tracingBus.subscribe(MarketTickEvent.class, event -> {
            capturedCorrelationId.set(TraceContext.getCorrelationId());
        });

        EventMetadata meta = new EventMetadata("evt-3", System.currentTimeMillis(),
                System.nanoTime(), 3L, null, 1);
        MarketTickEvent tick = new MarketTickEvent(meta, 3L, "INFY",
                ExchangeSegment.NSE_EQ, FeedMode.TICKER, 150000L, 200L, 80000L,
                System.currentTimeMillis(), Optional.empty(), 8000L, 8000L);

        tracingBus.publish(tick);

        assertNull(capturedCorrelationId.get(),
                "TraceContext should remain null when event has no correlation ID");
    }

    @Test
    @DisplayName("Publish delegates to underlying EventBus")
    void publishDelegatesToDelegate() {
        EventMetadata meta = new EventMetadata("evt-4", System.currentTimeMillis(),
                System.nanoTime(), 4L, "corr-delegate", 1);
        MarketTickEvent tick = new MarketTickEvent(meta, 4L, "SBIN",
                ExchangeSegment.NSE_EQ, FeedMode.TICKER, 80000L, 500L, 100000L,
                System.currentTimeMillis(), Optional.empty(), 10000L, 10000L);

        tracingBus.publish(tick);

        assertEquals(1, delegate.published.size());
        assertSame(tick, delegate.published.get(0));
    }

    /**
     * Simple recording EventBus for testing.
     */
    static class RecordingEventBus implements EventBus {
        final List<DomainEvent> published = new CopyOnWriteArrayList<>();
        final List<DomainEventHandler<?>> handlers = new CopyOnWriteArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            handlers.add(handler);
        }

        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            handlers.remove(handler);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void publish(DomainEvent event) {
            published.add(event);
            for (DomainEventHandler handler : handlers) {
                handler.onEvent(event);
            }
        }

        @Override public void start() {}
        @Override public void stop() {}
    }
}
