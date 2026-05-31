package com.tradej.hotpath;

import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.event.TickReceived;
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

    @Test
    public void testHighThroughputDisruptorRtt() throws Exception {
        // 1. Setup in-process LMAX Disruptor
        EventBus bus = createMinimalBus();
        
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicLong tickCount = new AtomicLong();
        int targetTickCount = 30000; // 6 seconds of 5000 ticks/sec
        CountDownLatch latch = new CountDownLatch(targetTickCount);

        bus.subscribe(TickReceived.class, e -> {
            long currentTick = tickCount.incrementAndGet();
            // Filter out the first 5,000 ticks as JIT warmup phase to measure true steady state p99
            if (currentTick > 5000) {
                long latency = System.currentTimeMillis() - e.metadata().timestampMs();
                latencies.add(latency);
            }
            latch.countDown();
        });

        bus.start();

        // 2. Spawn publishers simulating 5000 ticks/sec across 500 sharded symbols (500 ticks/sec per thread)
        int threadCount = 10;
        int ticksPerThread = targetTickCount / threadCount;
        Thread[] threads = new Thread[threadCount];

        long stressStartTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            threads[t] = new Thread(() -> {
                long nextTickTime = System.nanoTime();
                for (int i = 0; i < ticksPerThread; i++) {
                    String symbol = "SYM-" + ((threadIndex * ticksPerThread + i) % 500);
                    // Standard Indian market tick format
                    TickReceived tick = new TickReceived(
                        new EventMetadata(
                            UUID.randomUUID().toString(),
                            System.currentTimeMillis(),
                            System.nanoTime(),
                            0L,
                            "",
                            1
                        ),
                        symbol,
                        "1m",
                        100000L,
                        10L,
                        1000L,
                        System.currentTimeMillis(),
                        null
                    );
                    bus.publish(tick);
                    
                    // Throttle to exactly 500 ticks/sec per thread (5000 total across 10 threads)
                    // 500 ticks/sec = 1 tick every 2ms = 2,000,000 nanoseconds
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
        boolean completed = latch.await(15, TimeUnit.SECONDS);
        bus.stop();

        long totalDuration = System.currentTimeMillis() - stressStartTime;
        assertTrue(completed, "High-throughput stress run timed out! Processed: " + tickCount.get() + "/" + targetTickCount);

        // 3. Compute 99th percentile execution RTT
        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);

        int p99Index = (int) (sortedLatencies.size() * 0.99);
        long p99Latency = sortedLatencies.get(p99Index);

        System.out.println("Stress Test Stats: Total Ticks=" + tickCount.get() + ", Measured Ticks=" + latencies.size() + ", DurationMs=" + totalDuration + ", p99 RTT=" + p99Latency + "ms");
        
        // Assert RTT is extremely low (< 100ms is standard safe limit for JUnit parallel execution overhead under high CI CPU contention)
        assertTrue(p99Latency < 100.0, "99th percentile RTT exceeds safe parallel load limit: " + p99Latency + "ms");
    }

    private static EventBus createMinimalBus() {
        var candleAgg = new CandleAggregationService(List.of("1m"));
        var portfolio = new PortfolioEngine(1_000_000L, 10_000_000L);
        var strategy = new StrategyEngine(List.of(), new com.tradej.core.domain.event.EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock()));
        var cb = new TradingCircuitBreaker();
        var idReg = new OrderIdentityRegistry();
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        var execHandler = new ExecutionHandler(null, runtimeModeHolder, cb, idReg, DeadLetterQueue.noop());

        var riskHandler = new PositionRiskHandler(new RiskLimits(10, 10, 10000000L, 5), () -> java.util.Collections.emptyMap());

        return new DisruptorEventBus(
            riskHandler, candleAgg, strategy, execHandler,
            portfolio, StageTimings.NO_OP, null, DeadLetterQueue.noop()
        );
    }
}
