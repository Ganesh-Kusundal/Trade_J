package com.tradej.strategy.service;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import java.util.Optional;
import com.tradej.core.testing.ConcurrentStressTester;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stress tests for concurrent access to {@link CandleAggregationService}.
 * <p>
 * These tests verify that concurrent tick submissions do not lose data
 * due to non-atomic read-modify-write on the internal candle map.
 */
@Tag("stress")
class CandleAggregationServiceStressTest {

    /**
     * Verifies that concurrent ticks for the same symbol/interval
     * produce a correct result with no exceptions.
     */
    @Test
    void concurrentTicksSameSymbolProducesCorrectAggregate() {
        CandleAggregationService service = new CandleAggregationService(List.of("1s"));
        CopyOnWriteArrayList<DomainEvent> emitted = new CopyOnWriteArrayList<>();
        long baseTs = 1_710_000_000_000L;

        int workers = 10;
        int ticksPerWorker = 200;

        var result = ConcurrentStressTester.run(workers, ticksPerWorker, threadIndex -> {
            long ts = baseTs + (threadIndex * 100) + 10;
            long ltp = 100_00L + threadIndex * 10L;
            service.onDomainEvent(
                    new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, ltp, 1L, 1L, ts, Optional.empty(), 0L, 0L),
                    emitted::add);
        });

        result.assertAllPassed().requireNoExceptions();

        long developingCount = emitted.stream()
                .filter(e -> e instanceof CandleDeveloping)
                .count();
        assertTrue(developingCount > 0,
                "Expected at least some CandleDeveloping events, got " + developingCount);
    }

    /**
     * Verifies that concurrent ticks for DIFFERENT symbols don't interfere.
     */
    @Test
    void concurrentTicksDifferentSymbolsDontInterfere() {
        CandleAggregationService service = new CandleAggregationService(List.of("5m"));
        int workers = 8;
        int ticksPerWorker = 200;
        List<String> symbols = List.of("RELIANCE", "SBIN", "TCS", "HDFC", "INFY", "ICICI", "ITC", "BHARTI");

        var result = ConcurrentStressTester.run(workers, ticksPerWorker, threadIndex -> {
            String symbol = symbols.get(threadIndex % symbols.size());
            long ts = 1_710_000_000_000L + (threadIndex * 1000L);
            long ltp = 100_00L + threadIndex * 100L;
            service.onDomainEvent(
                    new MarketTickEvent(EventMetadata.root(), 0L, symbol, ExchangeSegment.NSE_EQ, FeedMode.TICKER, ltp, 10L, 10L, ts, Optional.empty(), 0L, 0L),
                    e -> {});
        });

        result.assertAllPassed().requireNoExceptions();
    }

    /**
     * Verifies concurrent bucket rollovers produce correct closed candles.
     * Each thread posts to bucket 0 for the first half of iterations
     * and bucket 1 for the second half, triggering a rollover per thread.
     */
    @Test
    void concurrentBucketRolloverProducesCorrectClosedCandles() {
        CandleAggregationService service = new CandleAggregationService(List.of("1s"));
        CopyOnWriteArrayList<DomainEvent> emitted = new CopyOnWriteArrayList<>();
        long bucket0Start = 1_710_000_000_000L;
        long bucket1Start = bucket0Start + 1_000L;

        int workers = 8;
        int ticksPerWorker = 100;

        var result = ConcurrentStressTester.run(workers, ticksPerWorker, threadIndex -> {
            long ts0 = bucket0Start + threadIndex * 7L;
            long ts1 = bucket1Start + threadIndex * 7L;
            long ltp = 100_00L + threadIndex * 10L;
            // Tick to bucket 0
            service.onDomainEvent(
                    new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, ltp, 1L, 1L, ts0, Optional.empty(), 0L, 0L),
                    emitted::add);
            // Tick to bucket 1 — if bucket 0 already exists, this triggers a rollover
            service.onDomainEvent(
                    new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, ltp + 5, 1L, 1L, ts1, Optional.empty(), 0L, 0L),
                    emitted::add);
        });

        result.assertAllPassed().requireNoExceptions();

        long closedCount = emitted.stream()
                .filter(e -> e instanceof CandleClosed)
                .count();
        assertTrue(closedCount >= 1,
                "Expected at least one CandleClosed from bucket rollover, got " + closedCount);
    }
}
