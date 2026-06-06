package com.tradej.core.domain.value;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class InstrumentTypeTest {

    @Test
    void parseEquity() {
        assertEquals(InstrumentType.EQUITY, InstrumentType.parse("EQ"));
        assertEquals(InstrumentType.EQUITY, InstrumentType.parse("EQUITY"));
        assertEquals(InstrumentType.EQUITY, InstrumentType.parse("STOCK"));
    }

    @Test
    void parseFuture() {
        assertEquals(InstrumentType.FUTURE, InstrumentType.parse("FUT"));
        assertEquals(InstrumentType.FUTURE, InstrumentType.parse("FUTURES"));
        assertEquals(InstrumentType.FUTURE, InstrumentType.parse("FUTURE"));
    }

    @Test
    void parseOptions() {
        assertEquals(InstrumentType.OPTION_CALL, InstrumentType.parse("CE"));
        assertEquals(InstrumentType.OPTION_CALL, InstrumentType.parse("CALL"));
        assertEquals(InstrumentType.OPTION_PUT, InstrumentType.parse("PE"));
        assertEquals(InstrumentType.OPTION_PUT, InstrumentType.parse("PUT"));
        assertEquals(InstrumentType.OPTION_CALL, InstrumentType.parse("OPTCE"));
        assertEquals(InstrumentType.OPTION_PUT, InstrumentType.parse("OPTPE"));
    }

    @Test
    void parseIndex() {
        assertEquals(InstrumentType.INDEX, InstrumentType.parse("INDEX"));
    }

    @Test
    void parseCurrencyAndCommodity() {
        assertEquals(InstrumentType.CURRENCY_FUTURE, InstrumentType.parse("CURR_FUT"));
        assertEquals(InstrumentType.CURRENCY_OPTION, InstrumentType.parse("CURR_OPT"));
        assertEquals(InstrumentType.COMMODITY_FUTURE, InstrumentType.parse("COMM_FUT"));
    }

    @Test
    void parseNullReturnsUnknown() {
        assertEquals(InstrumentType.UNKNOWN, InstrumentType.parse(null));
        assertEquals(InstrumentType.UNKNOWN, InstrumentType.parse(""));
        assertEquals(InstrumentType.UNKNOWN, InstrumentType.parse("   "));
    }

    @Test
    void fallbackStartsWithFut() {
        assertEquals(InstrumentType.FUTURE, InstrumentType.parse("FUTSTOCK"));
    }

    @Test
    void isOptionCorrect() {
        assertTrue(InstrumentType.OPTION_CALL.isOption());
        assertTrue(InstrumentType.OPTION_PUT.isOption());
        assertTrue(InstrumentType.CURRENCY_OPTION.isOption());
        assertFalse(InstrumentType.EQUITY.isOption());
        assertFalse(InstrumentType.FUTURE.isOption());
    }

    @Test
    void isFutureCorrect() {
        assertTrue(InstrumentType.FUTURE.isFuture());
        assertTrue(InstrumentType.CURRENCY_FUTURE.isFuture());
        assertTrue(InstrumentType.COMMODITY_FUTURE.isFuture());
        assertFalse(InstrumentType.EQUITY.isFuture());
        assertFalse(InstrumentType.OPTION_CALL.isFuture());
    }

    @Test
    void isEquityCorrect() {
        assertTrue(InstrumentType.EQUITY.isEquity());
        assertFalse(InstrumentType.FUTURE.isEquity());
        assertFalse(InstrumentType.OPTION_CALL.isEquity());
    }
}
