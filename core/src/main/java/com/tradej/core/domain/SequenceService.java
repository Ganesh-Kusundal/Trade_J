package com.tradej.core.domain;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Monotonically increasing sequence number generator for canonical events.
 * <p>
 * Every {@link com.tradej.core.domain.event.DomainEvent published} into the Disruptor pipeline
 * receives an internal sequence number from this service. Broker-provided sequence numbers
 * are preserved as metadata but are <em>never</em> trusted for ordering — only internal
 * sequencing is authoritative.
 * <p>
 * Thread-safe. Uses {@link AtomicLong} with no contention under normal load.
 */
public final class SequenceService {

    private final AtomicLong sequence = new AtomicLong(0);

    /** Returns the next sequence number. */
    public long next() {
        return sequence.incrementAndGet();
    }

    /** Returns the current sequence number without incrementing. */
    public long current() {
        return sequence.get();
    }

    /**
     * Resets the sequence to a specific value.
     * Intended for testing and replay mode only.
     */
    public void reset(long value) {
        sequence.set(value);
    }
}
