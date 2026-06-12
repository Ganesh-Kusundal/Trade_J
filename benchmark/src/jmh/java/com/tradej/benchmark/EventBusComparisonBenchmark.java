package com.tradej.benchmark;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.disruptor.DisruptorEventBus;
import com.tradej.disruptor.config.DisruptorPipelineConfig;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionConfig;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.pipeline.runtime.PipelineRuntimeBridge;
import org.openjdk.jmh.annotations.*;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 3)
@State(Scope.Benchmark)
public class EventBusComparisonBenchmark {

    private EventBus simpleBus;
    private EventBus disruptorBus;
    private EventBus virtualThreadBus;

    private MarketTickEvent event;

    private final AtomicLong simpleCounter = new AtomicLong();
    private final AtomicLong disruptorCounter = new AtomicLong();
    private final AtomicLong virtualThreadCounter = new AtomicLong();

    @Setup
    public void setup() {
        event = new MarketTickEvent(
                new EventMetadata("bench", System.currentTimeMillis(), System.nanoTime(), 0, "bench-corr", 1),
                1L, "RELIANCE", ExchangeSegment.NSE_EQ, FeedMode.FULL,
                250000L, 100L, 50000L, System.currentTimeMillis(),
                Optional.empty(), 0L, 0L
        );

        // 1. Setup SimpleEventBus
        simpleBus = new SimpleEventBus();
        simpleBus.subscribe(MarketTickEvent.class, e -> simpleCounter.incrementAndGet());
        simpleBus.start();

        // 2. Setup DisruptorEventBus with minimal configuration
        var riskHandler = new PositionRiskHandler(RiskLimits.conservative(), () -> java.util.Collections.emptyMap());
        var execHandler = new ExecutionHandler(null, null, null, null, null, null, ExecutionConfig.DEFAULTS);
        PipelineRuntimeBridge bridge = new PipelineRuntimeBridge() {
            @Override public java.util.concurrent.atomic.AtomicReference<com.tradej.pipeline.runtime.GraphRuntime> runtimeRef() { return new java.util.concurrent.atomic.AtomicReference<>(); }
            @Override public com.tradej.pipeline.graph.PipelineGraph activeGraph() { return null; }
            @Override public void compileHotPath(java.util.function.Consumer<DomainEvent> hotPathPublisher) {}
            @Override public void reload(com.tradej.pipeline.graph.PipelineGraph graph, java.util.function.Consumer<DomainEvent> hotPathPublisher) {}
        };

        var config = new DisruptorPipelineConfig(
                riskHandler,
                null,
                null,
                execHandler,
                null,
                StageTimings.NO_OP,
                null,
                null,
                bridge,
                false,
                com.tradej.core.domain.runtime.RuntimeMode.LIVE,
                null
        );
        disruptorBus = new DisruptorEventBus(config);
        disruptorBus.subscribe(MarketTickEvent.class, e -> disruptorCounter.incrementAndGet());
        disruptorBus.start();

        // 3. Setup VirtualThreadEventBus
        virtualThreadBus = new VirtualThreadEventBus();
        virtualThreadBus.subscribe(MarketTickEvent.class, e -> virtualThreadCounter.incrementAndGet());
        virtualThreadBus.start();
    }

    @TearDown
    public void tearDown() {
        simpleBus.stop();
        disruptorBus.stop();
        virtualThreadBus.stop();
    }

    @Benchmark
    public long publishSimple() {
        simpleBus.publish(event);
        return simpleCounter.get();
    }

    @Benchmark
    public long publishDisruptor() {
        disruptorBus.publish(event);
        return disruptorCounter.get();
    }

    @Benchmark
    public long publishVirtualThread() {
        virtualThreadBus.publish(event);
        return virtualThreadCounter.get();
    }

    @Benchmark
    @Threads(4)
    public long publishSimpleMulti() {
        simpleBus.publish(event);
        return simpleCounter.get();
    }

    @Benchmark
    @Threads(4)
    public long publishDisruptorMulti() {
        disruptorBus.publish(event);
        return disruptorCounter.get();
    }

    @Benchmark
    @Threads(4)
    public long publishVirtualThreadMulti() {
        virtualThreadBus.publish(event);
        return virtualThreadCounter.get();
    }
}
