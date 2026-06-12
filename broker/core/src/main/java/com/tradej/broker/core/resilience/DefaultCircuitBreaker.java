package com.tradej.broker.core.resilience;

import com.tradej.core.domain.port.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Thread-safe circuit breaker implementation with CLOSED → OPEN → HALF_OPEN state machine.
 */
public final class DefaultCircuitBreaker implements CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(DefaultCircuitBreaker.class);

    private final String name;
    private final Config config;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicInteger successes = new AtomicInteger();
    private volatile Instant openedAt;

    public DefaultCircuitBreaker(String name, Config config) {
        this.name = name;
        this.config = config;
    }

    public DefaultCircuitBreaker(String name) {
        this(name, Config.defaults());
    }

    @Override
    public <T> T execute(Supplier<T> supplier) {
        State current = state.get();

        if (current == State.OPEN) {
            if (shouldAttemptReset()) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
                log.info("[CB:{}] Transitioning OPEN → HALF_OPEN", name);
            } else {
                throw new CircuitBreakerOpenException(name);
            }
        }

        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    @Override
    public void execute(Runnable runnable) {
        execute(() -> { runnable.run(); return null; });
    }

    private void onSuccess() {
        failures.set(0);
        successes.incrementAndGet();
        State prev = state.getAndSet(State.CLOSED);
        if (prev != State.CLOSED) {
            log.info("[CB:{}] Transitioning {} → CLOSED after success", name, prev);
        }
    }

    private void onFailure() {
        int count = failures.incrementAndGet();
        if (count >= config.failureThreshold()) {
            State prev = state.getAndSet(State.OPEN);
            openedAt = Instant.now();
            if (prev != State.OPEN) {
                log.warn("[CB:{}] Transitioning {} → OPEN after {} failures", name, prev, count);
            }
        }
    }

    private boolean shouldAttemptReset() {
        Instant opened = openedAt;
        if (opened == null) return true;
        return Instant.now().isAfter(opened.plus(config.openDuration()));
    }

    @Override
    public State state() { return state.get(); }

    @Override
    public int failureCount() { return failures.get(); }

    @Override
    public int successCount() { return successes.get(); }

    @Override
    public void reset() {
        failures.set(0);
        state.set(State.CLOSED);
        openedAt = null;
        log.info("[CB:{}] Manually reset to CLOSED", name);
    }
}
