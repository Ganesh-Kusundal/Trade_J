package com.tradej.composition;

import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.TradingClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Sanity tests for {@link ClockComposition}. Tests for {@link FullComposition}'s
 * brokerOnly path require SPI broker providers which the composition test classpath
 * does not include; that coverage is exercised in {@code :app:test} once Phase 2B
 * wires the Spring path.
 */
class FullCompositionTest {

    @Test
    void clockComposition_live_returnsValidTriple() {
        ClockComposition c = ClockComposition.live();
        assertNotNull(c.clock());
        assertNotNull(c.tradingClock());
        assertNotNull(c.eventMetadataFactory());
        assertNotNull(((EventMetadataFactory) c.eventMetadataFactory()));
    }

    @Test
    void clockComposition_replay_usesFixedEpochInIst() {
        ClockComposition c = ClockComposition.replay();
        Clock clock = c.clock();
        assertEquals(ZoneId.of("Asia/Kolkata"), clock.getZone());
        assertEquals(Instant.EPOCH, clock.instant());
        assertNotNull(c.tradingClock());
    }

    @Test
    void clockComposition_liveWithCallerClock_rejectsNull() {
        assertThrows(NullPointerException.class, () -> ClockComposition.live(null));
    }

    @Test
    void clockComposition_liveWithCallerClock_usesIt() {
        Clock fixed = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"));
        ClockComposition c = ClockComposition.live(fixed);
        assertSame(fixed, c.clock());
        TradingClock tradingClock = c.tradingClock();
        assertNotNull(tradingClock);
        // LiveTradingClock(Clock) delegates to the supplied clock
        assertEquals(Instant.parse("2024-01-15T10:00:00Z"), tradingClock.instant());
    }
}
