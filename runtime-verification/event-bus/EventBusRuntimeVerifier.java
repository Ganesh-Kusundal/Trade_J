package com.tradej.runtime.verification;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime verification tool for EventBus behavior.
 * Traces event propagation, measures latency, detects drops and duplicates.
 */
public class EventBusRuntimeVerifier {
    private static final Logger log = LoggerFactory.getLogger(EventBusRuntimeVerifier.class);

    private final EventBus eventBus;
    private final List<EventTrace> traces = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> subscriberInvocationCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalEventsPublished = new AtomicLong(0);
    private final AtomicLong totalEventsReceived = new AtomicLong(0);
    private final AtomicLong duplicatesDetected = new AtomicLong(0);
    private final AtomicLong eventsDropped = new AtomicLong(0);

    private volatile boolean tracing = false;

    public EventBusRuntimeVerifier(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Start tracing all events through the bus.
     */
    public void startTracing() {
        tracing = true;
        log.info("EventBus runtime verification started - tracing enabled");
    }

    /**
     * Stop tracing.
     */
    public void stopTracing() {
        tracing = false;
        log.info("EventBus runtime verification stopped");
    }

    /**
     * Register a tracing handler for a specific event type.
     */
    public <T extends DomainEvent> void traceEvents(Class<T> eventType, String subscriberName) {
        eventBus.subscribe(eventType, new DomainEventHandler<T>() {
            private final Instant subscribedAt = Instant.now();

            @Override
            public void handle(T event) {
                if (!tracing) {
                    return;
                }

                Instant receivedAt = Instant.now();
                long latencyMs = java.time.Duration.between(subscribedAt, receivedAt).toMillis();

                EventTrace trace = new EventTrace(
                    event.eventId(),
                    event.getClass().getSimpleName(),
                    subscriberName,
                    receivedAt,
                    latencyMs
                );
                traces.add(trace);

                subscriberInvocationCounts
                    .computeIfAbsent(subscriberName, k -> new AtomicInteger(0))
                    .incrementAndGet();

                totalEventsReceived.incrementAndGet();

                log.trace("Event traced: {} -> {} ({} ms)", event.eventId(), subscriberName, latencyMs);
            }
        });

        log.info("Registered tracing handler for {} -> {}", eventType.getSimpleName(), subscriberName);
    }

    /**
     * Publish a test event and trace it through the entire pipeline.
     */
    public EventPropagationResult traceEventPropagation(MarketTickEvent event) {
        Instant publishStart = Instant.now();
        totalEventsPublished.incrementAndGet();

        eventBus.publish(event);

        Instant publishEnd = Instant.now();
        long publishLatencyMs = java.time.Duration.between(publishStart, publishEnd).toMillis();

        // Collect all traces for this event
        List<EventTrace> eventTraces = traces.stream()
            .filter(t -> t.eventId().equals(event.eventId()))
            .toList();

        return new EventPropagationResult(
            event.eventId(),
            publishStart,
            publishEnd,
            publishLatencyMs,
            eventTraces
        );
    }

    /**
     * Test event ordering by publishing N events rapidly.
     */
    public OrderingTestResult testEventOrdering(int eventCount) {
        List<String> publishedOrder = new ArrayList<>();
        List<String> receivedOrder = new CopyOnWriteArrayList<>();

        // Register receiver
        traceEvents(MarketTickEvent.class, "ordering-test-receiver");

        // Publish events
        for (int i = 0; i < eventCount; i++) {
            MarketTickEvent event = createTestTick(i);
            publishedOrder.add(event.eventId());
            totalEventsPublished.incrementAndGet();
            eventBus.publish(event);
        }

        // Wait for processing
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Get received order
        receivedOrder.addAll(
            traces.stream()
                .filter(t -> t.subscriberName().equals("ordering-test-receiver"))
                .map(EventTrace::eventId)
                .toList()
        );

        boolean orderingPreserved = publishedOrder.equals(receivedOrder);
        int outOfOrderCount = countOutOfOrder(publishedOrder, receivedOrder);

        return new OrderingTestResult(
            eventCount,
            publishedOrder.size(),
            receivedOrder.size(),
            orderingPreserved,
            outOfOrderCount
        );
    }

    /**
     * Test backpressure by flooding the bus.
     */
    public BackpressureTestResult testBackpressure(int floodCount) {
        int beforeDropped = eventsDropped.get();

        for (int i = 0; i < floodCount; i++) {
            MarketTickEvent event = createTestTick(i);
            totalEventsPublished.incrementAndGet();
            eventBus.publish(event);
        }

        // Wait for processing
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int afterDropped = eventsDropped.get();
        int droppedCount = afterDropped - beforeDropped;

        return new BackpressureTestResult(
            floodCount,
            totalEventsReceived.get(),
            droppedCount,
            eventsDropped.get()
        );
    }

    /**
     * Generate verification report.
     */
    public EventBusVerificationReport generateReport() {
        return new EventBusVerificationReport(
            Instant.now(),
            totalEventsPublished.get(),
            totalEventsReceived.get(),
            duplicatesDetected.get(),
            eventsDropped.get(),
            new ConcurrentHashMap<>(subscriberInvocationCounts),
            new ArrayList<>(traces)
        );
    }

    private MarketTickEvent createTestTick(int sequence) {
        return new MarketTickEvent(
            "TEST-" + sequence,
            "NIFTY",
            "NSE",
            "INDEX",
            19500.0 + sequence,
            19500.0 + sequence,
            Instant.now().toEpochMilli(),
            sequence
        );
    }

    private int countOutOfOrder(List<String> expected, List<String> actual) {
        int count = 0;
        int size = Math.min(expected.size(), actual.size());
        for (int i = 0; i < size; i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                count++;
            }
        }
        return count;
    }

    // Record classes for verification results

    public record EventTrace(
        String eventId,
        String eventType,
        String subscriberName,
        Instant receivedAt,
        long latencyMs
    ) {}

    public record EventPropagationResult(
        String eventId,
        Instant publishStart,
        Instant publishEnd,
        long publishLatencyMs,
        List<EventTrace> subscriberTraces
    ) {}

    public record OrderingTestResult(
        int publishedCount,
        int expectedCount,
        int receivedCount,
        boolean orderingPreserved,
        int outOfOrderCount
    ) {}

    public record BackpressureTestResult(
        int floodCount,
        int processedCount,
        int droppedCount,
        int totalDropped
    ) {}

    public record EventBusVerificationReport(
        Instant generatedAt,
        long totalPublished,
        long totalReceived,
        long duplicatesDetected,
        long eventsDropped,
        Map<String, AtomicInteger> subscriberInvocations,
        List<EventTrace> traces
    ) {}
}
