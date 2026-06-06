package com.tradej.hotpath;

import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import java.util.Optional;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.port.EventBus;
import com.tradej.disruptor.DisruptorEventBus;
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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stress")
public class DisruptorHighThroughputStressTest {

    /**
     * Maximum p99 latency threshold in milliseconds. The 250ms ceiling accounts
     * for CI environments with CPU contention, background GC pauses, and
     * JUnit parallel-execution overhead. This is intentionally generous — the
     * test verifies the Disruptor processes 30k events without catastrophic
     * latency spikes, not sub-millisecond performance.
     */
    private static final long P99_LATENCY_THRESHOLD_MS = 250;

    /** Number of warmup ticks discarded before latency measurement begins. */
    private static final int WARMUP_TICKS = 10_000;

    /** Total ticks to publish (including warmup). */
    private static final int TARGET_TICK_COUNT = 40_000;

    /** Max retry attempts for the latency assertion. */
    private static final int MAX_RETRIES = 3;

    @Test
    public void testHighThroughputDisruptorRtt() throws Exception {
        long lastP99 = -1;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            lastP99 = runStressIteration(attempt);
            if (lastP99 < P99_LATENCY_THRESHOLD_MS) {
                return; // pass
            }
            System.out.printf("Attempt %d/%d: p99=%dms exceeds %dms threshold, retrying…%n",
                    attempt, MAX_RETRIES, lastP99, P99_LATENCY_THRESHOLD_MS);
            Thread.sleep(500); // brief cooldown between attempts
        }
        assertTrue(lastP99 < P99_LATENCY_THRESHOLD_MS,
                "99th percentile RTT exceeded " + P99_LATENCY_THRESHOLD_MS + "ms after "
                        + MAX_RETRIES + " attempts: " + lastP99 + "ms");
    }

    private long runStressIteration(int attempt) throws Exception {
        // 1. Setup in-process LMAX Disruptor
        EventBus bus = createMinimalBus();

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicLong tickCount = new AtomicLong();
        CountDownLatch latch = new CountDownLatch(TARGET_TICK_COUNT);

        bus.subscribe(MarketTickEvent.class, e -> {
            long currentTick = tickCount.incrementAndGet();
            if (currentTick > WARMUP_TICKS) {
                long latency = System.currentTimeMillis() - e.metadata().timestampMs();
                latencies.add(latency);
            }
            latch.countDown();
        });

        bus.start();

        // 2. Spawn publishers simulating 5000 ticks/sec across 500 sharded symbols
        int threadCount = 10;
        int ticksPerThread = TARGET_TICK_COUNT / threadCount;
        Thread[] threads = new Thread[threadCount];

        long stressStartTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            threads[t] = new Thread(() -> {
                long nextTickTime = System.nanoTime();
                for (int i = 0; i < ticksPerThread; i++) {
                    String symbol = "SYM-" + ((threadIndex * ticksPerThread + i) % 500);
                    MarketTickEvent tick = new MarketTickEvent(new EventMetadata(
                            UUID.randomUUID().toString(),
                            System.currentTimeMillis(),
                            System.nanoTime(),
                            0L,
                            "",
                            1
                        ), 0L, symbol, ExchangeSegment.NSE_EQ, FeedMode.TICKER, 100000L, 10L, 1000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);
                    bus.publish(tick);

                    // Throttle to exactly 500 ticks/sec per thread (5000 total across 10 threads)
                    nextTickTime += 2_000_000;
                    long sleepTimeNanos = nextTickTime - System.nanoTime();
                    if (sleepTimeNanos > 0) {
                        java.util.concurrent.locks.LockSupport.parkNanos(sleepTimeNanos);
                    }
                }
            });
            threads[t].start();
        }

        // Wait for stress run completion
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        bus.stop();

        long totalDuration = System.currentTimeMillis() - stressStartTime;
        assertTrue(completed, "High-throughput stress run timed out! Processed: " + tickCount.get() + "/" + TARGET_TICK_COUNT);

        // 3. Compute 99th percentile execution RTT
        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);

        int p99Index = (int) (sortedLatencies.size() * 0.99);
        long p99Latency = sortedLatencies.get(p99Index);

        long gcCount = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(gc -> gc.getCollectionCount()).sum();

        System.out.printf("[Attempt %d] Stress Test Stats: Ticks=%d, Measured=%d, DurationMs=%d, p99 RTT=%dms, GC=%d%n",
                attempt, tickCount.get(), latencies.size(), totalDuration, p99Latency, gcCount);

        return p99Latency;
    }

    private static EventBus createMinimalBus() {
        var candleAgg = new CandleAggregationService(List.of("1m"));
        var portfolio = new PortfolioEngine(1_000_000L, 10_000_000L);
        var strategy = new StrategyEngine(List.of(), new com.tradej.core.domain.event.EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock()));
        var cb = new TradingCircuitBreaker();
        var idReg = new OrderIdentityRegistry();
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        var execHandler = new ExecutionHandler(null, runtimeModeHolder,
                new com.tradej.core.domain.time.LiveTradingClock(), cb, idReg, DeadLetterQueue.noop());

        var riskHandler = new PositionRiskHandler(RiskLimits.withOpenPositionQuantity(10, 10, 10000000L, 5), () -> java.util.Collections.emptyMap());
        var bridge = com.tradej.disruptor.testsupport.PassthroughNode.passthroughBridge();

        return new DisruptorEventBus(
            riskHandler, candleAgg, strategy, execHandler,
            portfolio, StageTimings.NO_OP, null, DeadLetterQueue.noop(), bridge
        );
    }
}
