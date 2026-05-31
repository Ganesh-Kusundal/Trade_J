package com.tradej.core.testing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestClockTest {

    @Test
    void fixedClockReturnsSameInstant() {
        var clock = TestClock.fixed(Instant.parse("2026-05-27T10:00:00Z"));
        assertEquals(Instant.parse("2026-05-27T10:00:00Z"), clock.instant());
    }

    @Test
    void fixedClockReturnsUtcZone() {
        var clock = TestClock.fixed(Instant.EPOCH);
        assertEquals(ZoneId.of("UTC"), clock.getZone());
    }

    @Test
    void advanceToUpdatesInstant() {
        var clock = TestClock.fixed(Instant.parse("2026-05-27T10:00:00Z"));
        clock.advanceTo(Instant.parse("2026-05-27T10:05:00Z"));
        assertEquals(Instant.parse("2026-05-27T10:05:00Z"), clock.instant());
    }

    @Test
    void advanceByMillisAddsDuration() {
        var clock = TestClock.fixed(Instant.parse("2026-05-27T10:00:00Z"));
        clock.advanceByMillis(300_000L);
        assertEquals(Instant.parse("2026-05-27T10:05:00Z"), clock.instant());
    }

    @Test
    void monotonicNanosAreDeterministic() {
        var clock1 = TestClock.fixed(Instant.EPOCH);
        var clock2 = TestClock.fixed(Instant.EPOCH);
        for (int i = 0; i < 100; i++) {
            assertEquals(clock1.monotonicNanos(), clock2.monotonicNanos());
        }
    }

    @Test
    void monotonicNanosAdvanceMonotonically() {
        var clock = TestClock.fixed(Instant.EPOCH);
        long prev = clock.monotonicNanos();
        for (int i = 0; i < 1000; i++) {
            long next = clock.monotonicNanos();
            assertEquals(prev + 1_000_000L, next);
            prev = next;
        }
    }

    @Test
    void fixedFromEpochMillis() {
        var clock = TestClock.fixed(0L);
        assertEquals(Instant.EPOCH, clock.instant());
    }

    @Test
    void withZoneReturnsSameClock() {
        var clock = TestClock.fixed(Instant.EPOCH);
        var zoned = clock.withZone(ZoneId.of("Asia/Tokyo"));
        // TestClock ignores zone changes
        assertEquals(clock.instant(), zoned.instant());
    }
}
