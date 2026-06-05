package com.tradej.broker.core.benchmark;

import com.tradej.broker.api.auth.TokenState;
import com.tradej.broker.api.auth.TokenSource;
import com.tradej.broker.core.auth.DefaultTokenLifecycleService;
import com.tradej.broker.core.auth.TokenStateStore;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH micro-benchmark for {@link DefaultTokenLifecycleService} token acquisition.
 *
 * <p>Measures the average time and throughput of {@code ensureValid()} under
 * concurrent access. The token is pre-seeded as expired so every call triggers
 * the (lightweight) refresh path.
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
public class TokenLifecycleBenchmark {

    private DefaultTokenLifecycleService service;

    @Setup
    public void setup() {
        TokenStateStore stateStore = new TokenStateStore() {
            private TokenState state;
            @Override public void save(TokenState state) { this.state = state; }
            @Override public TokenState load() { return state; }
        };
        // Seed an already-expired token so ensureValid() always refreshes
        stateStore.save(new TokenState("bench-token", "refresh", 0L, 0L, TokenSource.TOTP));

        service = new DefaultTokenLifecycleService(stateStore, 60_000L) {
            @Override
            protected TokenState doAcquire() {
                return new TokenState("new-token", "new-refresh",
                        System.currentTimeMillis() + 3_600_000L,
                        System.currentTimeMillis(), TokenSource.TOTP);
            }
            @Override
            protected TokenState doRefresh(String refreshToken) {
                return doAcquire();
            }
        };
    }

    @Benchmark
    public void ensureValid() {
        service.ensureValid();
    }
}
