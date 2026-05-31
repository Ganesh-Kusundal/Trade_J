package com.tradej.cli;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class CandlesCmdRangeTest {

    @Test
    void defaultsToNinetyDaysWhenNoDatesProvided() {
        TradeCli.DateRange range = TradeCli.CandlesCmd.resolveRange(null, null);
        long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1L;
        assertEquals(90L, days);
    }

    @Test
    void defaultsFromWhenOnlyToProvided() {
        LocalDate to = LocalDate.of(2026, 5, 28);
        TradeCli.DateRange range = TradeCli.CandlesCmd.resolveRange(null, to);
        assertEquals(to.minusDays(89), range.from());
        assertEquals(to, range.to());
    }

    @Test
    void rejectsInvalidRangeOrder() {
        assertThrows(IllegalArgumentException.class, () ->
                TradeCli.CandlesCmd.resolveRange(LocalDate.of(2026, 5, 29), LocalDate.of(2026, 5, 28)));
    }
}
