package com.tradej.core.domain.value;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PriceMathTest {

    @Test
    void toPaisa_wholeNumber() {
        assertEquals(25000_00L, PriceMath.toPaisa(new BigDecimal("25000")));
    }

    @Test
    void toPaisa_decimalValue() {
        assertEquals(25000_50L, PriceMath.toPaisa(new BigDecimal("25000.50")));
    }

    @Test
    void toPaisa_halfUpRounding() {
        assertEquals(100_01L, PriceMath.toPaisa(new BigDecimal("100.005")));
    }

    @Test
    void toPaisa_null_returnsZero() {
        assertEquals(0L, PriceMath.toPaisa((BigDecimal) null));
    }

    @Test
    void toPaisa_string_parses() {
        assertEquals(1234_56L, PriceMath.toPaisa("1234.56"));
    }

    @Test
    void toPaisa_nullString_returnsZero() {
        assertEquals(0L, PriceMath.toPaisa((String) null));
        assertEquals(0L, PriceMath.toPaisa(""));
        assertEquals(0L, PriceMath.toPaisa("  "));
    }

    @Test
    void fromPaisa_roundTrips() {
        long paisa = 25000_75L;
        BigDecimal price = PriceMath.fromPaisa(paisa);
        assertEquals(paisa, PriceMath.toPaisa(price));
    }

    @Test
    void fromPaisa_zero() {
        assertEquals(BigDecimal.ZERO, PriceMath.fromPaisa(0L).stripTrailingZeros());
    }

    @Test
    void fromPaisa_negativeValue() {
        BigDecimal price = PriceMath.fromPaisa(-500_00L);
        assertEquals(new BigDecimal("-500.00"), price);
    }
}
