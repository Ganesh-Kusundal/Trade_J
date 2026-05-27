package com.tradej.core.domain.value;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class PriceMathUnitTest {
    @Test
    void convertsBigDecimalPriceToPaisaAndBack() {
        long paisa = PriceMath.toPaisa(new BigDecimal("24500.50"));

        assertEquals(2_450_050L, paisa);
        assertEquals(new BigDecimal("24500.50"), PriceMath.fromPaisa(paisa));
    }

    @Test
    void handlesBlankStringAsZero() {
        assertEquals(0L, PriceMath.toPaisa(""));
    }
}
