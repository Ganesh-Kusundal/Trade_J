package com.tradej.benchmark;

import com.tradej.core.domain.event.*;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH benchmarks for the Trade-J event bus and indicator pipeline.
 *
 * Run with: ./gradlew :benchmark:jmh
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 3)
public class EventBusBenchmark {

    @State(Scope.Thread)
    public static class BusState {
        EventBus bus;
        MarketTickEvent event;
        AtomicLong counter = new AtomicLong();

        @Setup
        public void setup() {
            bus = new InMemoryEventBus();
            bus.subscribe(MarketTickEvent.class, e -> counter.incrementAndGet());
            bus.start();

            event = new MarketTickEvent(
                    new EventMetadata("bench", System.currentTimeMillis(), System.nanoTime(), 0, "bench-corr", 1),
                    1L, "RELIANCE", ExchangeSegment.NSE_EQ, FeedMode.FULL,
                    250000L, 100L, 50000L, System.currentTimeMillis(),
                    Optional.empty(), 0L, 0L
            );
        }

        @TearDown
        public void tearDown() {
            bus.stop();
        }
    }

    @Benchmark
    public long publishTick(BusState state) {
        state.bus.publish(state.event);
        return state.counter.get();
    }

    @Benchmark
    @Threads(4)
    public long publishTickMultiThreaded(BusState state) {
        state.bus.publish(state.event);
        return state.counter.get();
    }

    // Minimal in-memory event bus for benchmarking
    static class InMemoryEventBus implements EventBus {
        private DomainEventHandler<MarketTickEvent> handler;

        @Override
        @SuppressWarnings("unchecked")
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> h) {
            if (eventType == MarketTickEvent.class) {
                handler = (DomainEventHandler<MarketTickEvent>) h;
            }
        }

        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {}

        @Override
        public void publish(DomainEvent event) {
            if (handler != null && event instanceof MarketTickEvent mte) {
                handler.onEvent(mte);
            }
        }

        @Override public void start() {}
        @Override public void stop() {}
    }
}
