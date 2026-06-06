package com.tradej.broker.core.benchmark;

import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.explorer.BrokerExplorer;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.simulation.PaperBrokerConnection;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks BrokerExplorer.inspect() performance.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class BrokerExplorerBenchmark {

    private BrokerHandle handle;

    @Setup
    public void setup() {
        PaperBrokerConnection conn = new PaperBrokerConnection();
        handle = new BrokerHandle(BrokerSource.SIMULATION, conn);
    }

    @Benchmark
    public void inspect() {
        BrokerExplorer.inspect(handle);
    }

    @Benchmark
    public void capabilities() {
        handle.capabilities();
    }
}
