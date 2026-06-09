package com.tradej.broker.api.exception;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UnsupportedIntervalExceptionTest {

    @Test
    void carriesBrokerAndIntervalInfo() {
        var ex = new UnsupportedIntervalException("Dhan", "3m", Set.of("1m", "5m", "1d"));

        assertEquals("Dhan", ex.broker());
        assertEquals("3m", ex.requestedInterval());
        assertEquals(Set.of("1m", "5m", "1d"), ex.supportedIntervals());
    }

    @Test
    void messageContainsAllDetails() {
        var ex = new UnsupportedIntervalException("Upstox", "15m", Set.of("1minute", "30minute", "day"));

        assertTrue(ex.getMessage().contains("Upstox"));
        assertTrue(ex.getMessage().contains("15m"));
        assertTrue(ex.getMessage().contains("1minute"));
    }

    @Test
    void isIllegalArgumentException() {
        var ex = new UnsupportedIntervalException("ICICI", "2h", Set.of("1m", "5m"));
        assertInstanceOf(IllegalArgumentException.class, ex);
    }
}
