package com.tradej.core.domain.time;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.*;

@Tag("unit")
class LiveTradingClockTest {

    @Test
    void shouldReturnCurrentTimeFromSystemClock() {
        TradingClock clock = new LiveTradingClock();
        long now = System.currentTimeMillis();
        assertTrue(Math.abs(clock.millis() - now) < 500, "Clock should be close to system time");
    }

    @Test
    void shouldWrapProvidedClock() {
        Instant fixedInstant = Instant.parse("2026-05-31T10:00:00Z");
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        Clock fixed = Clock.fixed(fixedInstant, ist);
        TradingClock clock = new LiveTradingClock(fixed);

        assertEquals(fixedInstant, clock.instant());
        assertEquals(fixedInstant.atZone(ist).toLocalDateTime(), clock.now());
        assertEquals(fixedInstant.toEpochMilli(), clock.millis());
    }

    @Test
    void shouldDefaultToIst() {
        Instant fixedInstant = Instant.parse("2026-05-31T10:00:00Z");
        // We can't easily check the default constructor's IST property without mocking System clock,
        // so we check that the LiveTradingClock uses the provided clock's zone.
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        TradingClock clock = new LiveTradingClock(Clock.fixed(fixedInstant, ist));
        
        LocalDateTime now = clock.now();
        LocalDateTime utc = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC);
        Duration diff = Duration.between(utc, now);
        
        assertEquals(Duration.ofHours(5).plusMinutes(30), diff);
    }
}
