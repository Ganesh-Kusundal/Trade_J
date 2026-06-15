package com.tradej.core.testing;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @deprecated Use {@link com.tradej.core.domain.id.DeterministicIdGenerator} instead.
 * This class remains for backward compatibility with existing test code.
 */
@Deprecated
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
