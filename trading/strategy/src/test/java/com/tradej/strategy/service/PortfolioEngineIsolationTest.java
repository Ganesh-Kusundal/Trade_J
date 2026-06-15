package com.tradej.strategy.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.value.Side;
import com.tradej.strategy.portfolio.PortfolioEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-7: Tests that PortfolioEngine processes events on a dedicated thread,
 * not on the caller (ring buffer) thread.
 */
@Tag("unit")
class PortfolioEngineIsolationTest {

    private PortfolioEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stop();
        }
    }

    // ── Helpers ──

    private static SignalGenerated createSignal(String signalId) {
        return new SignalGenerated(
                EventMetadata.root(),
                signalId,
                "SBIN",
                "5m",
                Side.BUY,
                1_000_00L,
                0L,
                0L,
                "test",
                Map.of("quantity", 10L, "strategyName", "test-strategy")
        );
    }

    private static TradeClosed createTradeClosed(String tradeId) {
        return new TradeClosed(
                EventMetadata.root(),
                tradeId,
                "SBIN",
                1_100_00L,
                1_000_00L,
                10L,
                "target-hit"
        );
    }

    // ── P0-7 Tests ──

    @Test
    void processingDoesNotBlockCallerThread() throws Exception {
        engine = new PortfolioEngine();
        engine.start();

        AtomicReference<String> callerThreadName = new AtomicReference<>();
        AtomicReference<String> processingThreadName = new AtomicReference<>();
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch releaseProcessing = new CountDownLatch(1);

        // Use a downstream that records the processing thread and blocks
        // so we can verify the caller returned before processing completed.
        java.util.function.Consumer<DomainEvent> blockingDownstream = event -> {
            processingThreadName.set(Thread.currentThread().getName());
            processingStarted.countDown();
            try {
                releaseProcessing.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        callerThreadName.set(Thread.currentThread().getName());
        // Submit event — should return immediately (async)
        engine.onDomainEvent(createSignal("sig-async-1"), blockingDownstream);

        // Caller should NOT be blocked — verify processing thread is different
        assertTrue(processingStarted.await(5, TimeUnit.SECONDS),
                "Processing should have started on the portfolio-engine thread");

        assertNotEquals(callerThreadName.get(), processingThreadName.get(),
                "PortfolioEngine must process events on its own thread, not the caller thread");
        assertTrue(processingThreadName.get().contains("portfolio-engine"),
                "Processing thread should be named 'portfolio-engine', got: " + processingThreadName.get());

        releaseProcessing.countDown();
    }

    @Test
    void eventsAreProcessedInOrder() throws Exception {
        engine = new PortfolioEngine();
        engine.start();

        List<String> processedIds = new CopyOnWriteArrayList<>();
        CountDownLatch allProcessed = new CountDownLatch(10);

        java.util.function.Consumer<DomainEvent> collectingDownstream = event -> {
            if (event instanceof SignalGenerated sig) {
                processedIds.add(sig.signalId());
            }
            allProcessed.countDown();
        };

        // Submit 10 events in order
        for (int i = 0; i < 10; i++) {
            engine.onDomainEvent(createSignal("sig-order-" + i), collectingDownstream);
        }

        assertTrue(allProcessed.await(10, TimeUnit.SECONDS),
                "All events should be processed within timeout");

        // Verify order
        assertEquals(10, processedIds.size(), "All 10 events should be processed");
        for (int i = 0; i < 10; i++) {
            assertEquals("sig-order-" + i, processedIds.get(i),
                    "Events must be processed in submission order at index " + i);
        }
    }

    @Test
    void queueBackpressureBlocksProducerWhenFull() throws Exception {
        engine = new PortfolioEngine();

        // Block the processing thread by using a downstream that blocks until released
        CountDownLatch firstEventProcessing = new CountDownLatch(1);
        CountDownLatch releaseAll = new CountDownLatch(1);
        CountDownLatch producerBlocked = new CountDownLatch(1);
        CountDownLatch producerReleased = new CountDownLatch(1);

        java.util.function.Consumer<DomainEvent> blockingDownstream = event -> {
            firstEventProcessing.countDown();
            try {
                releaseAll.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        engine.start();

        // Submit first event — it will block the processing thread
        engine.onDomainEvent(createSignal("sig-blocker"), blockingDownstream);
        assertTrue(firstEventProcessing.await(5, TimeUnit.SECONDS),
                "First event should start processing");

        // Fill the queue to capacity + 1 (4097 events) to guarantee the producer
        // blocks on put() once the queue is full. Use a separate thread so we can
        // observe the blocking behavior.
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 4097; i++) {
                engine.onDomainEvent(createSignal("sig-flood-" + i), blockingDownstream);
            }
            producerReleased.countDown();
        }, "test-producer");
        producer.start();

        // Wait for the queue to reach capacity — the producer will block on the 4097th put()
        int depth;
        long deadline = System.currentTimeMillis() + 5000;
        do {
            Thread.sleep(50);
            depth = engine.queueDepth();
        } while (depth < 4096 && System.currentTimeMillis() < deadline);

        assertTrue(depth >= 4096,
                "Queue should be nearly full (depth=" + depth + ")");
        assertTrue(producer.isAlive(),
                "Producer should be blocked on put() when queue is full");

        // Release the consumer — producer should unblock and finish
        releaseAll.countDown();
        assertTrue(producerReleased.await(10, TimeUnit.SECONDS),
                "Producer should complete after consumer drains queue");
        producer.join(5000);
    }
}
