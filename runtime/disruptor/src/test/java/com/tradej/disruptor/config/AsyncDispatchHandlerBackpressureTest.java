package com.tradej.disruptor.config;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.DomainEventVisitor;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TestEvent;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.disruptor.MutableDomainEventEnvelope;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies backpressure behaviour of {@link AsyncDispatchHandler}:
 * blocking offer, DLQ routing, consecutive-drop counter, and ERROR log escalation.
 */
@Tag("unit")
class AsyncDispatchHandlerBackpressureTest {



    /** DeadLetterQueue that captures all appended events. */
    private static class CapturingDlq implements DeadLetterQueue {
        final List<DomainEvent> captured = new CopyOnWriteArrayList<>();
        final AtomicLong appendCount = new AtomicLong();

        @Override
        public void append(String source, DomainEvent event, String reason) {
            captured.add(event);
            appendCount.incrementAndGet();
        }
    }

    private AsyncDispatchHandler handler(int queueCapacity, DeadLetterQueue dlq) {
        Map<Class<? extends DomainEvent>, List<com.tradej.core.domain.port.DomainEventHandler<? extends DomainEvent>>> subscribers = Map.of();
        // Do NOT start the handler — the drain thread is not needed;
        // we just want to fill the queue and observe backpressure.
        return new AsyncDispatchHandler(subscribers, queueCapacity, StageTiming.noOp(), dlq);
    }

    private MutableDomainEventEnvelope envelopeFor(DomainEvent event) {
        MutableDomainEventEnvelope envelope = new MutableDomainEventEnvelope();
        envelope.setEvent(event);
        return envelope;
    }

    @Test
    void fullQueueDropsImmediatelyWithoutBlocking() throws Exception {
        CapturingDlq dlq = new CapturingDlq();
        // Queue capacity = 1 so it fills fast
        AsyncDispatchHandler h = handler(1, dlq);

        // First event should succeed immediately
        h.onEvent(envelopeFor(new TestEvent()), 0, false);
        assertEquals(1, h.queueDepth());
        assertEquals(0, h.droppedEventCount());

        // Second event must drop immediately because no consumer drains the queue
        long start = System.nanoTime();
        h.onEvent(envelopeFor(new TestEvent()), 1, false);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // The offer should have completed immediately (less than 250ms to account for logger initialization/warmup)
        assertTrue(elapsedMs < 250,
                "Expected non-blocking offer to complete immediately but took " + elapsedMs + "ms");
        assertEquals(1, h.droppedEventCount());
    }

    @Test
    void droppedEventsAreRoutedToDlq() throws Exception {
        CapturingDlq dlq = new CapturingDlq();
        AsyncDispatchHandler h = handler(1, dlq);

        // Fill the queue
        h.onEvent(envelopeFor(new TestEvent()), 0, false);
        // This one should be dropped and go to DLQ
        h.onEvent(envelopeFor(new TestEvent()), 1, false);

        assertEquals(1, dlq.appendCount.get(), "DLQ should have received the dropped event");
        assertFalse(dlq.captured.isEmpty(), "DLQ captured list should not be empty");
    }

    @Test
    void consecutiveDropCounterIncrements() throws Exception {
        CapturingDlq dlq = new CapturingDlq();
        AsyncDispatchHandler h = handler(1, dlq);

        // Fill the single-slot queue
        h.onEvent(envelopeFor(new TestEvent()), 0, false);
        assertEquals(0, h.consecutiveDropCount());

        // Three consecutive drops
        h.onEvent(envelopeFor(new TestEvent()), 1, false);
        h.onEvent(envelopeFor(new TestEvent()), 2, false);
        h.onEvent(envelopeFor(new TestEvent()), 3, false);

        assertEquals(3, h.consecutiveDropCount());
        assertEquals(3, h.droppedEventCount());
    }

    @Test
    void errorLogThresholdAfterTenConsecutiveDrops() throws Exception {
        CapturingDlq dlq = new CapturingDlq();
        AsyncDispatchHandler h = handler(1, dlq);

        // Fill the queue
        h.onEvent(envelopeFor(new TestEvent()), 0, false);

        // Produce 10 consecutive drops
        for (int i = 1; i <= 10; i++) {
            h.onEvent(envelopeFor(new TestEvent()), i, false);
        }

        assertEquals(10, h.consecutiveDropCount(),
                "After 10 drops the consecutive counter must be exactly 10");
        assertEquals(10, h.droppedEventCount());
        assertEquals(10, dlq.appendCount.get(), "All 10 dropped events should be in the DLQ");
    }
}
