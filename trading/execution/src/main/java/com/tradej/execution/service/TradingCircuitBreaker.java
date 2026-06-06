package com.tradej.execution.service;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker with three states: CLOSED → OPEN → HALF_OPEN → CLOSED.
 * <p>
 * In {@link State#HALF_OPEN}, only a limited number of probe requests
 * are allowed through. A probe success transitions to CLOSED; a probe
 * failure transitions back to OPEN.
 * <p>
 * All state transitions use lock-free {@link AtomicReference} CAS operations
 * instead of {@code synchronized} to avoid single-monitor contention under
 * high order throughput (fixes C-01).
 */
public final class TradingCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openDurationMs;
    private final int maxHalfOpenProbes;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntilMs = new AtomicLong(0L);
    private final AtomicInteger halfOpenProbes = new AtomicInteger();

    public TradingCircuitBreaker() {
        this(5, 30_000L, 3);
    }

    public TradingCircuitBreaker(int failureThreshold, long openDurationMs) {
        this(failureThreshold, openDurationMs, 3);
    }

    public TradingCircuitBreaker(int failureThreshold, long openDurationMs, int maxHalfOpenProbes) {
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
        this.maxHalfOpenProbes = maxHalfOpenProbes;
    }

    /**
     * Returns true if the request is allowed through.
     * Lock-free: uses CAS on {@link #state} to handle the OPEN → HALF_OPEN
     * transition. Brief races in HALF_OPEN probe counting are acceptable —
     * the downstream broker will reject excess requests.
     */
    public boolean allowsRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN) {
            if (System.currentTimeMillis() >= openUntilMs.get()) {
                // Try to transition to HALF_OPEN — only one thread will succeed
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenProbes.set(1);
                    return true;
                }
                // CAS failed — another thread transitioned; retry
                return allowsRequest();
            }
            return false;
        }
        // HALF_OPEN — CAS loop to atomically claim a probe slot
        if (current == State.HALF_OPEN) {
            int probes;
            do {
                probes = halfOpenProbes.get();
                if (probes >= maxHalfOpenProbes) {
                    return false;
                }
            } while (!halfOpenProbes.compareAndSet(probes, probes + 1));
            return true;
        }
        return true; // unreachable
    }

    public void recordSuccess() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            state.compareAndSet(State.HALF_OPEN, State.CLOSED);
        } else if (current == State.OPEN) {
            // Rescue the circuit to CLOSED if a successful probe completed from the same window
            if (halfOpenProbes.get() > 0) {
                state.compareAndSet(State.OPEN, State.CLOSED);
            }
        }
        consecutiveFailures.set(0);
        openUntilMs.set(0L);
        halfOpenProbes.set(0);
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            State current = state.get();
            if (current == State.CLOSED) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    openUntilMs.set(System.currentTimeMillis() + openDurationMs);
                } else {
                    // Another thread raced — still record the timeout extension
                    openUntilMs.set(System.currentTimeMillis() + openDurationMs);
                }
            } else if (current == State.HALF_OPEN) {
                if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    openUntilMs.set(System.currentTimeMillis() + openDurationMs);
                } else {
                    openUntilMs.set(System.currentTimeMillis() + openDurationMs);
                }
            } else if (current == State.OPEN) {
                // Extend the open window
                openUntilMs.set(System.currentTimeMillis() + openDurationMs);
            }
        }
    }

    public boolean isOpen() {
        State current = state.get();
        return current != State.CLOSED && current != State.HALF_OPEN;
    }

    public State currentState() {
        return state.get();
    }

    /** Reset to fully closed state. */
    public void reset() {
        state.set(State.CLOSED);
        consecutiveFailures.set(0);
        openUntilMs.set(0L);
        halfOpenProbes.set(0);
    }
}
