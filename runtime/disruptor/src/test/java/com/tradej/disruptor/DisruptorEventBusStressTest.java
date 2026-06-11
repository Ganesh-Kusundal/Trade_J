package com.tradej.disruptor;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import java.util.Optional;
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
import com.tradej.execution.service.ExecutionConfig;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
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
        bus.subscribe(MarketTickEvent.class, e -> {});
        bus.unsubscribe(MarketTickEvent.class, e -> {});
        bus.unsubscribe(MarketTickEvent.class, e -> {});
        bus.unsubscribe(DomainEvent.class, e -> {});
        assertTrue(true, "No exception thrown");
    }

    @Test
    void multipleSubscribersAllReceiveEvents() throws Exception {
        EventBus bus = createMinimalBus();
        AtomicInteger c1 = new AtomicInteger();
        AtomicInteger c2 = new AtomicInteger();

        bus.subscribe(MarketTickEvent.class, e -> c1.incrementAndGet());
        bus.subscribe(MarketTickEvent.class, e -> c2.incrementAndGet());
        bus.start();

        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 100_00L, 10L, 10L, 1000L, Optional.empty(), 0L, 0L);
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

        bus.subscribe(MarketTickEvent.class, e -> count.incrementAndGet());
        bus.start();

        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 100_00L, 10L, 10L, 1000L, Optional.empty(), 0L, 0L);
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

        bus.subscribe(MarketTickEvent.class, received::add);
        bus.start();

        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 100_00L, 10L, 10L, 1000L, Optional.empty(), 0L, 0L);
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
        CountDownLatch primaryLatch = new CountDownLatch(1);
        CountDownLatch reentrantLatch = new CountDownLatch(1);
        int expectedEvents = 100;
        AtomicLong seq = new AtomicLong();

        bus.subscribe(MarketTickEvent.class, e -> {
            int count = primaryCount.incrementAndGet();
            var candle = new CandleClosed(
                    EventMetadata.correlated(e.metadata().correlationId(), count),
                    new Candle("SBIN", "5m", 0, 5000, 100_00L, 105_00L, 95_00L, 102_00L, 1000L, true)
            );
            bus.publish(candle);
            if (count == expectedEvents) {
                primaryLatch.countDown();
            }
        });

        bus.subscribe(CandleClosed.class, e -> {
            int rc = reentrantCount.incrementAndGet();
            if (rc == expectedEvents) {
                reentrantLatch.countDown();
            }
        });
        bus.start();

        // Publish from multiple threads to create ring buffer pressure. Each
        // event gets a unique sequenceId so dedup does not collapse them.
        var result = ConcurrentStressTester.run(4, expectedEvents / 4, threadIndex -> {
            long id = seq.incrementAndGet();
            var tick = new MarketTickEvent(
                    EventMetadata.correlated("stress", id),
                    0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                    100_00L, 10L, 10L, 1000L, Optional.empty(), 0L, 0L);
            bus.publish(tick);
        });

        assertTrue(primaryLatch.await(60, TimeUnit.SECONDS),
                "Primary publish timed out — possible deadlock. primaryCount=" + primaryCount.get());
        assertTrue(reentrantLatch.await(60, TimeUnit.SECONDS),
                "Re-entrant events did not drain — downstream queue stuck. reentrantCount=" + reentrantCount.get());
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

        bus.subscribe(MarketTickEvent.class, e -> count.incrementAndGet());
        bus.start();

        // Publish 100K+ events with the SAME eventId — dedup should collapse to 1
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 100_00L, 10L, 10L, 1000L, Optional.empty(), 0L, 0L);
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

        bus.subscribe(MarketTickEvent.class, e -> {
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
            bus.publish(new MarketTickEvent(EventMetadata.correlated("dlq-test", i), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 100_00L, 10L, 10L, 1000L, Optional.empty(), 0L, 0L));
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
        var cb = new TradingCircuitBreaker();
        var idReg = new OrderIdentityRegistry();
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        var execHandler = new ExecutionHandler(null, runtimeModeHolder,
                new com.tradej.core.domain.time.LiveTradingClock(), cb, idReg, DeadLetterQueue.noop(), ExecutionConfig.DEFAULTS);

        var riskHandler = new PositionRiskHandler(RiskLimits.conservative(), () -> java.util.Collections.emptyMap());
        var bridge = com.tradej.disruptor.testsupport.PassthroughNode.passthroughBridge();

        return new com.tradej.disruptor.config.DisruptorPipelineBuilder()
                .positionRiskHandler(riskHandler)
                .candleAggregationService(candleAgg)
                .executionHandler(execHandler)
                .portfolioEngine(portfolio)
                .stageTimings(StageTimings.NO_OP)
                .deadLetterQueue(deadLetterQueue)
                .pipelineRuntimeBridge(bridge)
                .buildBus();
    }

    private static EventBus createMinimalBus() {
        var candleAgg = new CandleAggregationService(List.of("5m"));
        var portfolio = new PortfolioEngine(1_000_000L, 10_000_000L);
        var cb = new TradingCircuitBreaker();
        var idReg = new OrderIdentityRegistry();
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        var execHandler = new ExecutionHandler(null, runtimeModeHolder,
                new com.tradej.core.domain.time.LiveTradingClock(), cb, idReg, DeadLetterQueue.noop(), ExecutionConfig.DEFAULTS);

        var riskHandler = new PositionRiskHandler(RiskLimits.conservative(), () -> java.util.Collections.emptyMap());
        var bridge = com.tradej.disruptor.testsupport.PassthroughNode.passthroughBridge();

        return new com.tradej.disruptor.config.DisruptorPipelineBuilder()
                .positionRiskHandler(riskHandler)
                .candleAggregationService(candleAgg)
                .executionHandler(execHandler)
                .portfolioEngine(portfolio)
                .stageTimings(StageTimings.NO_OP)
                .deadLetterQueue(DeadLetterQueue.noop())
                .pipelineRuntimeBridge(bridge)
                .buildBus();
    }
}
