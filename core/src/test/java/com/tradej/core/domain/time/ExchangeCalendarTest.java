package com.tradej.core.domain.time;

import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeCalendarTest {

    @Test
    void nseEquity_openAt10() {
        assertTrue(ExchangeCalendar.isMarketOpen(ExchangeSegment.NSE_EQ, LocalTime.of(10, 0)));
    }

    @Test
    void nseEquity_closedAt16() {
        assertFalse(ExchangeCalendar.isMarketOpen(ExchangeSegment.NSE_EQ, LocalTime.of(16, 0)));
    }

    @Test
    void mcxCommodity_openAt22() {
        assertTrue(ExchangeCalendar.isMarketOpen(ExchangeSegment.MCX_COMM, LocalTime.of(22, 0)));
    }

    @Test
    void mcxCommodity_closedAt8() {
        assertFalse(ExchangeCalendar.isMarketOpen(ExchangeSegment.MCX_COMM, LocalTime.of(8, 0)));
    }

    @Test
    void preOpen_beforeRegularOpen() {
        ExchangeCalendar.SessionHours nseHours = ExchangeCalendar.sessionHours(ExchangeSegment.NSE_EQ);
        // Pre-open at 9:00, regular open at 9:15 — market should NOT be open at 9:10
        assertTrue(nseHours.preOpen().isBefore(nseHours.open()));
        assertFalse(ExchangeCalendar.isMarketOpen(ExchangeSegment.NSE_EQ, LocalTime.of(9, 10)));
    }

    @Test
    void mcxCommodity_preOpen_beforeRegularOpen() {
        ExchangeCalendar.SessionHours mcxHours = ExchangeCalendar.sessionHours(ExchangeSegment.MCX_COMM);
        // MCX pre-open at 8:45, regular open at 9:00
        assertTrue(mcxHours.preOpen().isBefore(mcxHours.open()));
        assertFalse(ExchangeCalendar.isMarketOpen(ExchangeSegment.MCX_COMM, LocalTime.of(8, 50)));
    }
}
