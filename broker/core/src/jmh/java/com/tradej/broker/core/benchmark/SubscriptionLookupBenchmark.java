package com.tradej.broker.core.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JMH micro-benchmark for subscription key lookup via {@link ConcurrentHashMap}.
 *
 * <p>Simulates the O(1) reverse-index lookup that the Upstox multiplexer uses
 * to route feed messages to the correct instrument.
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class SubscriptionLookupBenchmark {

    private static final int SUBSCRIPTION_COUNT = 10_000;

    private ConcurrentHashMap<String, String> index;
    private List<String> keys;

    @Setup
    public void setup() {
        index = new ConcurrentHashMap<>(SUBSCRIPTION_COUNT);
        keys = new ArrayList<>(SUBSCRIPTION_COUNT);
        for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
            String key = "NSE_EQ|SYMBOL" + i;
            index.put(key, "req" + i);
            keys.add(key);
        }
    }

    @Benchmark
    @Threads(1)
    public void lookupSingleThread(Blackhole bh) {
        String target = keys.get(0);
        bh.consume(index.get(target));
    }

    @Benchmark
    @Threads(4)
    public void lookupContended(Blackhole bh) {
        // Each thread picks a different key based on thread id to reduce
        // false sharing while still contending on the map's segments.
        int idx = (int) (Thread.currentThread().getId() % SUBSCRIPTION_COUNT);
        String target = keys.get(Math.abs(idx));
        bh.consume(index.get(target));
    }
}
