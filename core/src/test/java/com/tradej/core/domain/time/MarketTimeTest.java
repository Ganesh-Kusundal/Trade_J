package com.tradej.core.domain.time;

import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class MarketTimeTest {

    @Test
    void dayBoundsMakeInclusiveAndExclusiveContractsExplicit() {
        LocalDate date = LocalDate.of(2026, 6, 19);

        MarketTime.DayBounds inclusive = MarketTime.dayBoundsInclusive(date);
        MarketTime.DayBounds exclusive = MarketTime.dayBoundsExclusive(date);

        assertEquals(inclusive.startMs(), exclusive.startMs());
        assertEquals(inclusive.endMs() + 1L, exclusive.endMs());
    }

    @Test
    void sessionBoundsUseExchangeCalendarBySegment() {
        LocalDate date = LocalDate.of(2026, 6, 19);

        MarketTime.SessionBounds nse = MarketTime.sessionBounds(ExchangeSegment.NSE_EQ, date);
        MarketTime.SessionBounds mcx = MarketTime.sessionBounds(ExchangeSegment.MCX_COMM, date);

        assertEquals(MarketTime.atTimeMs(date, ExchangeCalendar.sessionHours(ExchangeSegment.NSE_EQ).open()), nse.openMs());
        assertEquals(MarketTime.atTimeMs(date, ExchangeCalendar.sessionHours(ExchangeSegment.MCX_COMM).close()), mcx.closeMs());
    }
}
