package com.tradej.core.domain.id;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Seeded, sequential {@link IdGenerator} that produces identical ID sequences
 * across runs when initialized with the same seed. Used in REPLAY and BACKTEST
 * modes to guarantee that signal, order, trade, and fill IDs are repeatable.
 * <p>
 * IDs follow the pattern {@code PREFIX-<monotonic counter>} so that every call
 * to any generation method advances the same counter. This guarantees global
 * uniqueness within a single run, which is sufficient for replay/backtest.
 */
public final class DeterministicIdGenerator implements IdGenerator {

    private final AtomicLong counter;

    /**
     * Creates a generator seeded at the given value. The first generated ID
     * will use {@code seed + 1}.
     *
     * @param seed starting counter value (inclusive); typical value is 0 or 1
     */
    public DeterministicIdGenerator(long seed) {
        this.counter = new AtomicLong(seed);
    }

    /** Creates a generator seeded at 1. */
    public DeterministicIdGenerator() {
        this(0L);
    }

    @Override
    public String generateSignalId() {
        return "SIG-" + counter.incrementAndGet();
    }

    @Override
    public String generateOrderId() {
        return "ORD-" + counter.incrementAndGet();
    }

    @Override
    public String generateTradeId() {
        return "TRD-" + counter.incrementAndGet();
    }

    @Override
    public String generateFillId() {
        return "FILL-" + counter.incrementAndGet();
    }

    @Override
    public String generateEventId() {
        return "EVT-" + counter.incrementAndGet();
    }

    /** Returns the current counter value (for testing/verification). */
    public long currentCounter() {
        return counter.get();
    }

    /** Resets the counter to the given seed (for reuse across replay runs). */
    public void reset(long seed) {
        counter.set(seed);
    }
}
