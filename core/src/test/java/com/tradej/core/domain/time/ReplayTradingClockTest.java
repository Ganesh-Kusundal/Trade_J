package com.tradej.core.domain.time;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.*;

@Tag("unit")
class ReplayTradingClockTest {

    @Test
    void shouldReturnStartInstant() {
        Instant start = Instant.parse("2026-05-31T10:00:00Z");
        ReplayTradingClock clock = new ReplayTradingClock(start);
        assertEquals(start, clock.instant());
    }

    @Test
    void shouldAdvanceToNewInstant() {
        Instant start = Instant.parse("2026-05-31T10:00:00Z");
        Instant next = Instant.parse("2026-05-31T10:00:01Z");
        ReplayTradingClock clock = new ReplayTradingClock(start);
        
        clock.advanceTo(next);
        
        assertEquals(next, clock.instant());
    }

    @Test
    void shouldReturnNowInIst() {
        Instant start = Instant.parse("2026-05-31T10:00:00Z"); // 15:30 IST
        ReplayTradingClock clock = new ReplayTradingClock(start);
        
        LocalDateTime expected = LocalDateTime.of(2026, 5, 31, 15, 30);
        assertEquals(expected, clock.now());
    }
}
