package com.tradej.core.testing;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic ID generator using a seeded counter.
 * Replaces {@link java.util.UUID#randomUUID()} in simulation/replay contexts
 * to guarantee same seed → same IDs across runs.
 */
public final class DeterministicIdGenerator {

    private final AtomicLong counter;

    public DeterministicIdGenerator(long seed) {
        this.counter = new AtomicLong(seed);
    }

    /**
     * Returns the next sequential ID.
     */
    public long nextId() {
        return counter.incrementAndGet();
    }

    /**
     * Returns a deterministic string event ID.
     */
    public String generateEventId() {
        return "EVT-" + counter.incrementAndGet();
    }

    /**
     * Returns a deterministic order ID.
     */
    public String generateOrderId() {
        return "ORD-" + counter.incrementAndGet();
    }

    /**
     * Returns a deterministic trade ID.
     */
    public String generateTradeId() {
        return "TRD-" + counter.incrementAndGet();
    }

    /**
     * Returns a deterministic signal ID.
     */
    public String generateSignalId() {
        return "SIG-" + counter.incrementAndGet();
    }
}
