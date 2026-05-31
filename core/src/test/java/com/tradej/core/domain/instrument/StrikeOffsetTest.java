package com.tradej.core.domain.instrument;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class StrikeOffsetTest {

    @Test
    void atmPlusMinusGeneratesSymmetricOffsets() {
        assertEquals(List.of(-1, 0, 1), StrikeOffset.atmPlusMinus(1).stream().map(StrikeOffset::value).toList());
    }

    @Test
    void parseSpecHandlesAtmTokens() {
        assertEquals(0, StrikeOffset.parseSpec("ATM").value());
        assertEquals(3, StrikeOffset.parseSpec("ATM+3").value());
        assertEquals(-2, StrikeOffset.parseSpec("ATM-2").value());
    }

    @Test
    void rejectsOutOfRangeOffsets() {
        assertThrows(IllegalArgumentException.class, () -> new StrikeOffset(11));
        assertThrows(IllegalArgumentException.class, () -> StrikeOffset.atmPlusMinus(11));
    }
}
