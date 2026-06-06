package com.tradej.broker.core.benchmark;

import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.core.resilience.CircuitBreakerConfig;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks CircuitBreaker throughput under various configurations.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CircuitBreakerBenchmark {

    private CircuitBreaker defaultBreaker;
    private CircuitBreaker aggressiveBreaker;

    @Setup
    public void setup() {
        defaultBreaker = new CircuitBreaker(CircuitBreakerConfig.DEFAULT);
        aggressiveBreaker = new CircuitBreaker(CircuitBreakerConfig.AGGRESSIVE);
    }

    @Benchmark
    public void assertCanExecute_default() {
        defaultBreaker.assertCanExecute("benchmark-op");
    }

    @Benchmark
    public void assertCanExecute_aggressive() {
        aggressiveBreaker.assertCanExecute("benchmark-op");
    }

    @Benchmark
    public void onSuccess_default() {
        defaultBreaker.onSuccess("benchmark-op");
    }

    @Benchmark
    public void fullCycle_default() {
        defaultBreaker.assertCanExecute("benchmark-op");
        defaultBreaker.onSuccess("benchmark-op");
    }

    @Benchmark
    public void isOpen_default() {
        defaultBreaker.isOpen("benchmark-op");
    }
}
