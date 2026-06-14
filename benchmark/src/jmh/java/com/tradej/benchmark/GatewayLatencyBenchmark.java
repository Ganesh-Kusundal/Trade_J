package com.tradej.benchmark;

import com.tradej.gateway.protocol.GatewayBinaryCodec;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.transport.WebSocketTransport;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmarks for gateway publish latency (p50/p99/p999) and throughput.
 *
 * Run with: ./gradlew :benchmark:jmh -Pbenchmarks
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 3)
public class GatewayLatencyBenchmark {

    @State(Scope.Thread)
    public static class RouterState {
        GatewayTopicRouter router;
        byte[] tickPayload;
        AtomicLong received = new AtomicLong();

        @Setup
        public void setup() {
            router = new GatewayTopicRouter(4096, 4096);
            router.start();

            // Realistic tick payload (~200 bytes JSON)
            tickPayload = GatewayBinaryCodec.utf8(
                    "{\"symbol\":\"RELIANCE\",\"exchangeSegment\":\"NSE_EQ\","
                    + "\"ltpPaisa\":250000,\"lastTradeQuantity\":100,"
                    + "\"cumulativeVolume\":50000,\"exchangeTimestampEpochMs\":"
                    + System.currentTimeMillis() + ",\"openInterest\":5000}"
            );

            // Add a mock transport subscribed to MARKET_TICK
            WebSocketTransport mockTransport = new WebSocketTransport() {
                final String id = UUID.randomUUID().toString();
                @Override public String id() { return id; }
                @Override public boolean isOpen() { return true; }
                @Override public void sendBinary(byte[] data) { received.incrementAndGet(); }
                @Override public void close() {}
            };
            router.subscribe(mockTransport, GatewayTopic.MARKET_TICK);
        }

        @TearDown
        public void tearDown() {
            router.stop();
        }
    }

    @Benchmark
    public long publishTickLatency(RouterState state, Blackhole bh) {
        state.router.publish(GatewayTopic.MARKET_TICK, state.tickPayload);
        return state.received.get();
    }

    @Benchmark
    @Threads(4)
    public long publishTickMultiThreaded(RouterState state, Blackhole bh) {
        state.router.publish(GatewayTopic.MARKET_TICK, state.tickPayload);
        return state.received.get();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public long publishTickThroughput(RouterState state) {
        state.router.publish(GatewayTopic.MARKET_TICK, state.tickPayload);
        return state.received.get();
    }
}
