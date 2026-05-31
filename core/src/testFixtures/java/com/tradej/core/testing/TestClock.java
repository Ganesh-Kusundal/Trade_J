package com.tradej.core.testing;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controllable clock for deterministic testing.
 * Provides both wall-clock millis and monotonic nanos that advance together.
 * <p>
 * Usage:
 * <pre>{@code
 * var clock = new TestClock(Instant.parse("2026-05-27T10:00:00Z"));
 * // Events created with this clock will have deterministic timestamps
 * clock.advanceTo(Instant.parse("2026-05-27T10:05:00Z"));
 * // Now events will use the advanced time
 * }</pre>
 */
public final class TestClock extends Clock {

    private final ZoneId zone;
    private final AtomicLong epochMillis;
    private final AtomicLong monotonicNanos;

    public TestClock(Instant initial) {
        this.zone = ZoneId.of("UTC");
        this.epochMillis = new AtomicLong(initial.toEpochMilli());
        this.monotonicNanos = new AtomicLong(0L);
    }

    /**
     * Creates a TestClock fixed at the given instant.
     */
    public static TestClock fixed(Instant instant) {
        return new TestClock(instant);
    }

    /**
     * Creates a TestClock fixed at the given epoch millis.
     */
    public static TestClock fixed(long epochMillis) {
        return new TestClock(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Advances the clock to the specified instant.
     */
    public void advanceTo(Instant instant) {
        this.epochMillis.set(instant.toEpochMilli());
    }

    /**
     * Advances the clock by the given duration from current time.
     */
    public void advanceByMillis(long millis) {
        this.epochMillis.addAndGet(millis);
    }

    /**
     * Returns the current simulated time as an Instant.
     */
    public Instant currentInstant() {
        return Instant.ofEpochMilli(epochMillis.get());
    }

    /**
     * Returns a monotonic timestamp suitable for EventMetadata.
     * Advances by 1ms each call to simulate time progression.
     */
    public long monotonicNanos() {
        return monotonicNanos.addAndGet(1_000_000L); // 1ms in nanos
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(epochMillis.get());
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this; // ignore zone changes for testing
    }
}
