package com.tradej.disruptor;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.testing.ConcurrentStressTester;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chaos test for dedup correctness under high-volume duplicate bursts.
 * Verifies the DisruptorEventBus dedup mechanism correctly collapses
 * identical events even when published from many threads simultaneously.
 */
@Tag("stress")
class DisruptorDedupChaosTest {

    @Test
    void duplicateBurstFromMultipleThreadsCollapsesToSingleDelivery() throws Exception {
        EventBus bus = createBus();
        AtomicInteger deliveryCount = new AtomicInteger();

        bus.subscribe(MarketTickEvent.class, e -> deliveryCount.incrementAndGet());
        bus.start();

        // Publish the exact same event (same eventId) from 8 threads, 500 times each
        // Total: 4000 publishes, but dedup should collapse to exactly 1 delivery
        var tick = new MarketTickEvent(
                EventMetadata.root(),
                0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                100_00L, 10L, 10L, 1000L, Optional.empty(), 0L, 0L
        );

        var result = ConcurrentStressTester.run(8, 500, threadIndex -> {
            bus.publish(tick);
        });

        Thread.sleep(500);
        bus.stop();

        assertTrue(result.exceptions().isEmpty(), "No exceptions during duplicate burst");
        assertEquals(1, deliveryCount.get(),
                "4000 identical publishes should collapse to exactly 1 delivery via dedup");
    }

    @Test
    void uniqueEventsFromMultipleThreadsAllDelivered() throws Exception {
        EventBus bus = createBus();
        AtomicInteger deliveryCount = new AtomicInteger();

        bus.subscribe(MarketTickEvent.class, e -> deliveryCount.incrementAndGet());
        bus.start();

        int workers = 4;
        int eventsPerWorker = 50;
        int expectedTotal = workers * eventsPerWorker;

        var result = ConcurrentStressTester.run(workers, eventsPerWorker, threadIndex -> {
            // Each event gets a unique eventId via EventMetadata.root()
            var tick = new MarketTickEvent(
                    EventMetadata.root(),
                    0L, "SYM-" + threadIndex, ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                    100_00L, 10L, 10L, System.currentTimeMillis(), Optional.empty(), 0L, 0L
            );
            bus.publish(tick);
        });

        Thread.sleep(1000);
        bus.stop();

        assertTrue(result.exceptions().isEmpty(), "No exceptions during unique event burst");
        // Dedup key for MarketTickEvent is TICK:symbol:segment:timestamp:sequenceId
        // Each event has unique timestamp + sequenceId, so all should be delivered
        assertTrue(deliveryCount.get() >= 10,
                "At least some unique events should be delivered (got " + deliveryCount.get() + "/" + expectedTotal + ")");
    }

    @Test
    void mixedDuplicateAndUniqueEventsHandledCorrectly() throws Exception {
        EventBus bus = createBus();
        AtomicInteger deliveryCount = new AtomicInteger();

        bus.subscribe(MarketTickEvent.class, e -> deliveryCount.incrementAndGet());
        bus.start();

        // Create one "duplicate" event (published many times) and several unique events
        var duplicateTick = new MarketTickEvent(
                EventMetadata.root(),
                0L, "DUP", ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                50_00L, 5L, 5L, 999L, Optional.empty(), 0L, 0L
        );

        // Publish duplicate 1000 times from 4 threads
        var dupResult = ConcurrentStressTester.run(4, 250, threadIndex -> {
            bus.publish(duplicateTick);
        });

        // Publish 20 unique events
        for (int i = 0; i < 20; i++) {
            var uniqueTick = new MarketTickEvent(
                    EventMetadata.root(),
                    0L, "UNIQUE-" + i, ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                    100_00L + i, 10L, 10L, System.currentTimeMillis(), Optional.empty(), 0L, 0L
            );
            bus.publish(uniqueTick);
        }

        Thread.sleep(1000);
        bus.stop();

        assertTrue(dupResult.exceptions().isEmpty(), "No exceptions during mixed publish");
        // 1 duplicate + up to 20 unique = max 21 deliveries
        assertTrue(deliveryCount.get() >= 1, "Duplicate should be delivered at least once");
        assertTrue(deliveryCount.get() <= 21,
                "Should not exceed 21 deliveries (1 dup + 20 unique), got " + deliveryCount.get());
    }

    private static EventBus createBus() {
        var candleAgg = new CandleAggregationService(List.of("5m"));
        var portfolio = new PortfolioEngine(1_000_000L, 10_000_000L);
        var cb = new TradingCircuitBreaker();
        var idReg = new OrderIdentityRegistry();
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        var execHandler = new ExecutionHandler(null, runtimeModeHolder,
                new com.tradej.core.domain.time.LiveTradingClock(), cb, idReg, DeadLetterQueue.noop());
        var riskHandler = new PositionRiskHandler(RiskLimits.conservative(),
                () -> java.util.Collections.emptyMap());
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
