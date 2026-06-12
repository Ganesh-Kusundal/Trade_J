package com.tradej.pipeline.clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.TradingClock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression test: ensures {@link VirtualClock} satisfies the {@link TradingClock}
 * contract so it can drive the OMS and MatchingEngine in determinism-sensitive tests.
 */
@Tag("unit")
class VirtualClockAsTradingClockTest {

    @Test
    void virtualClockSatisfiesTradingClockContract() {
        VirtualClock vc = new VirtualClock(VirtualClock.Mode.REPLAY);
        TradingClock tc = vc; // compile-time proof of implements

        assertNotNull(tc.instant());
        assertEquals(tc.instant().toEpochMilli(), tc.millis());
        assertNotNull(tc.now());
    }

    @Test
    void virtualClockAndLiveTradingClockAreInterchangeable() {
        // Use a far-future instant to defeat the Math.max() semantics of advanceVirtualTimeMs.
        Instant fixed = Instant.parse("2099-12-31T03:30:00Z");
        VirtualClock vc = new VirtualClock(VirtualClock.Mode.REPLAY);
        vc.advanceVirtualTimeMs(fixed.toEpochMilli());
        TradingClock live = new LiveTradingClock(java.time.Clock.fixed(fixed, java.time.ZoneId.of("UTC")));

        assertEquals(live.millis(), ((TradingClock) vc).millis());
        assertEquals(live.instant(), ((TradingClock) vc).instant());
    }

    @Test
    void virtualClockReplayTimeIsStable() {
        // Use a far-future instant to defeat the Math.max() semantics of advanceVirtualTimeMs.
        Instant fixed = Instant.parse("2099-12-31T10:00:00Z");
        VirtualClock vc = new VirtualClock(VirtualClock.Mode.REPLAY);
        vc.advanceVirtualTimeMs(fixed.toEpochMilli());

        TradingClock tc = vc;
        assertEquals(fixed, tc.instant());
        assertEquals(fixed.toEpochMilli(), tc.millis());
        assertEquals(LocalDateTime.ofInstant(fixed, java.time.ZoneId.of("Asia/Kolkata")), tc.now());
    }

    @Test
    void virtualClockLiveModeTracksSystemClock() {
        VirtualClock vc = new VirtualClock(VirtualClock.Mode.LIVE);
        TradingClock tc = vc;

        long sys = System.currentTimeMillis();
        long diff = Math.abs(tc.millis() - sys);
        assertTrue(diff < 1000, "Live-mode VirtualClock should be within 1s of system time, got diff=" + diff);
    }
}
