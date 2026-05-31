package com.tradej.disruptor;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.testing.ConcurrentStressTester;
import com.tradej.core.testing.StressTestResult;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.StrategyEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stress tests for {@link DisruptorEventBus}.
 */
@Tag("stress")
class DisruptorEventBusStressTest {

    @Test
    void unsubscribeUnregisteredTypeDoesNotThrow() {
        EventBus bus = createMinimalBus();
        bus.subscribe(TickReceived.class, e -> {});
        bus.unsubscribe(TickReceived.class, e -> {});
        bus.unsubscribe(TickReceived.class, e -> {});
        bus.unsubscribe(DomainEvent.class, e -> {});
        assertTrue(true, "No exception thrown");
    }

    @Test
    void multipleSubscribersAllReceiveEvents() throws Exception {
        EventBus bus = createMinimalBus();
        AtomicInteger c1 = new AtomicInteger();
        AtomicInteger c2 = new AtomicInteger();

        bus.subscribe(TickReceived.class, e -> c1.incrementAndGet());
        bus.subscribe(TickReceived.class, e -> c2.incrementAndGet());
        bus.start();

        var tick = new TickReceived(EventMetadata.root(), "SBIN", "5m",
                100_00L, 10L, 10L, 1000L, null);
        bus.publish(tick);
        Thread.sleep(200);
        bus.stop();

        assertEquals(1, c1.get());
        assertEquals(1, c2.get());
    }

    @Test
    void dedupPreventsDuplicateEvents() throws Exception {
        EventBus bus = createMinimalBus();
        AtomicInteger count = new AtomicInteger();

        bus.subscribe(TickReceived.class, e -> count.incrementAndGet());
        bus.start();

        var tick = new TickReceived(EventMetadata.root(), "SBIN", "5m",
                100_00L, 10L, 10L, 1000L, null);
        bus.publish(tick);
        bus.publish(tick);
        Thread.sleep(200);
        bus.stop();

        assertEquals(1, count.get());
    }

    @Test
    void downstreamQueueFlushesOnShutdown() throws Exception {
        EventBus bus = createMinimalBus();
        CopyOnWriteArrayList<DomainEvent> received = new CopyOnWriteArrayList<>();

        bus.subscribe(TickReceived.class, received::add);
        bus.start();

        var tick = new TickReceived(EventMetadata.root(), "SBIN", "5m",
                100_00L, 10L, 10L, 1000L, null);
        bus.publish(tick);
        Thread.sleep(200);
        bus.stop();

        assertEquals(1, received.size());
    }

    @Test
    void reentrantPublishFromSubscriberDoesNotDeadlock() throws Exception {
        EventBus bus = createMinimalBus();
        AtomicInteger primaryCount = new AtomicInteger();
        AtomicInteger reentrantCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        int expectedEvents = 1000;

        bus.subscribe(TickReceived.class, e -> {
            int count = primaryCount.incrementAndGet();
            // Publish a different event from inside the subscriber callback —
            // this goes through the downstream queue and must not deadlock.
            bus.publish(new CandleClosed(
                    EventMetadata.correlated(e.metadata().correlationId(), count),
                    new Candle("SBIN", "5m", 0, 5000, 100_00L, 105_00L, 95_00L, 102_00L, 1000L, true)
            ));
            if (count == expectedEvents) {
                latch.countDown();
            }
        });

        bus.subscribe(CandleClosed.class, e -> reentrantCount.incrementAndGet());
        bus.start();

        // Publish from multiple threads to create ring buffer pressure
        var result = ConcurrentStressTester.run(10, expectedEvents / 10, threadIndex -> {
            var tick = new TickReceived(EventMetadata.root(), "SBIN", "5m",
                    100_00L, 10L, 10L, 1000L, null);
            bus.publish(tick);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "Re-entrant publish timed out — possible deadlock");
        bus.stop();

        assertEquals(expectedEvents, primaryCount.get(),
                "All primary events should be received");
        assertEquals(expectedEvents, reentrantCount.get(),
                "All re-entrant events should be received");
        assertTrue(result.passes() > 0,
                "ConcurrentStressTester should have passes");
    }

    @Test
    void dedupHandlesHighVolumeDuplicates() throws Exception {
        EventBus bus = createMinimalBus();
        AtomicInteger count = new AtomicInteger();

        bus.subscribe(TickReceived.class, e -> count.incrementAndGet());
        bus.start();

        // Publish 100K+ events with the SAME eventId — dedup should collapse to 1
        var tick = new TickReceived(EventMetadata.root(), "SBIN", "5m",
                100_00L, 10L, 10L, 1000L, null);
        for (int i = 0; i < 100_001; i++) {
            bus.publish(tick);
        }

        Thread.sleep(500);
        bus.stop();

        assertEquals(1, count.get(),
                "Dedup should collapse 100K+ duplicates to exactly 1 event");
    }

    @Test
    void dispatchQueueFullDropsToDeadLetterQueue() throws Exception {
        var dropCount = new AtomicLong();
        var droppedEvents = new CopyOnWriteArrayList<String>();

        DeadLetterQueue testDlq = (source, event, reason) -> {
            dropCount.incrementAndGet();
            droppedEvents.add(source);
        };

        EventBus bus = createBusWithDlq(testDlq);
        // Blocking latch: subscriber waits until we release it, causing dispatch queue backpressure
        var blocker = new CountDownLatch(1);
        AtomicInteger processedCount = new AtomicInteger();

        bus.subscribe(TickReceived.class, e -> {
            processedCount.incrementAndGet();
            try {
                blocker.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        bus.start();

        // Publish 5000 events — dispatch queue (4096 capacity) will overflow
        for (int i = 0; i < 5000; i++) {
            bus.publish(new TickReceived(
                    EventMetadata.correlated("dlq-test", i),
                    "SBIN", "5m",
                    100_00L, 10L, 10L, 1000L, null
            ));
        }

        // Poll for DLQ overflow with timeout (replaces fixed sleep to avoid flakiness)
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (dropCount.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertTrue(dropCount.get() > 0,
                "Events should have been dropped to DLQ when dispatch queue overflowed");
        assertTrue(droppedEvents.contains("async-dispatch"),
                "Dropped events should come from async-dispatch source");

        // Release the subscriber and stop quickly — the blocker latch prevents slow drain
        blocker.countDown();
        bus.stop();
    }

    private static EventBus createBusWithDlq(DeadLetterQueue deadLetterQueue) {
        var candleAgg = new CandleAggregationService(List.of("5m"));
        var portfolio = new PortfolioEngine(1_000_000L, 10_000_000L);
        var strategy = new StrategyEngine(List.of());
        var cb = new TradingCircuitBreaker();
        var idReg = new OrderIdentityRegistry();
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        var execHandler = new ExecutionHandler(null, null, runtimeModeHolder, cb, idReg, DeadLetterQueue.noop());

        InstrumentResolver noopResolver = new InstrumentResolver() {
            public Instrument resolve(InstrumentKey key) { throw new UnsupportedOperationException(); }
            public Instrument getBySymbol(InstrumentKey key) { throw new UnsupportedOperationException(); }
            public List<Instrument> allInstruments() { return List.of(); }
            public Instrument resolveBySecurityId(String id) { throw new UnsupportedOperationException(); }
            public Instrument requireDefinition(InstrumentKey key) { throw new UnsupportedOperationException(); }
            public Instrument resolvePayload(Object p) { throw new UnsupportedOperationException(); }
            public boolean isLoaded() { return true; }
            public int catalogSize() { return 0; }
        };

        var riskHandler = new PositionRiskHandler(noopResolver, RiskLimits.conservative(), portfolio);

        return new DisruptorEventBus(
                riskHandler, candleAgg, strategy, execHandler,
                portfolio, StageTimings.NO_OP, null, deadLetterQueue
        );
    }

    private static EventBus createMinimalBus() {
        var candleAgg = new CandleAggregationService(List.of("5m"));
        var portfolio = new PortfolioEngine(1_000_000L, 10_000_000L);
        var strategy = new StrategyEngine(List.of());
        var cb = new TradingCircuitBreaker();
        var idReg = new OrderIdentityRegistry();
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        var execHandler = new ExecutionHandler(null, null, runtimeModeHolder, cb, idReg, DeadLetterQueue.noop());

        InstrumentResolver noopResolver = new InstrumentResolver() {
            public Instrument resolve(InstrumentKey key) { throw new UnsupportedOperationException(); }
            public Instrument getBySymbol(InstrumentKey key) { throw new UnsupportedOperationException(); }
            public List<Instrument> allInstruments() { return List.of(); }
            public Instrument resolveBySecurityId(String id) { throw new UnsupportedOperationException(); }
            public Instrument requireDefinition(InstrumentKey key) { throw new UnsupportedOperationException(); }
            public Instrument resolvePayload(Object p) { throw new UnsupportedOperationException(); }
            public boolean isLoaded() { return true; }
            public int catalogSize() { return 0; }
        };

        var riskHandler = new PositionRiskHandler(noopResolver, RiskLimits.conservative(), portfolio);

        return new DisruptorEventBus(
                riskHandler, candleAgg, strategy, execHandler,
                portfolio, StageTimings.NO_OP, null, DeadLetterQueue.noop()
        );
    }
}
