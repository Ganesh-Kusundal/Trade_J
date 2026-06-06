package com.tradej.core.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductTypeTest {

    @Test
    void allExistingValuesStillExist() {
        // Verify the four original enum constants are still present
        assertNotNull(ProductType.valueOf("INTRADAY"));
        assertNotNull(ProductType.valueOf("CNC"));
        assertNotNull(ProductType.valueOf("MARGIN"));
        assertNotNull(ProductType.valueOf("CARRY_FORWARD"));
    }

    @Test
    void delivery_canonicalMapsToCnc() {
        assertEquals(ProductType.CNC, ProductType.DELIVERY.canonical());
    }

    @Test
    void intradayMargin_canonicalMapsToIntraday() {
        assertEquals(ProductType.INTRADAY, ProductType.INTRADAY_MARGIN.canonical());
    }

    @Test
    void marginFunding_canonicalMapsToMargin() {
        assertEquals(ProductType.MARGIN, ProductType.MARGIN_FUNDING.canonical());
    }

    @Test
    void intraday_canonicalReturnsSelf() {
        assertEquals(ProductType.INTRADAY, ProductType.INTRADAY.canonical());
    }

    @Test
    void cnc_canonicalReturnsSelf() {
        assertEquals(ProductType.CNC, ProductType.CNC.canonical());
    }

    @Test
    void carryForward_canonicalReturnsSelf() {
        assertEquals(ProductType.CARRY_FORWARD, ProductType.CARRY_FORWARD.canonical());
    }
}
