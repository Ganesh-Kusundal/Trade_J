package com.tradej.core.domain.instrument;

import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExchangeTickSizeRegistryTest {

    @Test
    void nseEquity_returns5Paisa() {
        assertEquals(5L, ExchangeTickSizeRegistry.tickSizePaisa(ExchangeSegment.NSE_EQ));
    }

    @Test
    void mcxCommodity_returns1Paisa() {
        assertEquals(1L, ExchangeTickSizeRegistry.tickSizePaisa(ExchangeSegment.MCX_COMM));
    }

    @Test
    void nseCurrency_returns25Paisa() {
        assertEquals(25L, ExchangeTickSizeRegistry.tickSizePaisa(ExchangeSegment.NSE_CURRENCY));
    }

    @Test
    void unknownSegment_returnsDefault5() {
        assertEquals(5L, ExchangeTickSizeRegistry.tickSizePaisa(ExchangeSegment.UNKNOWN));
    }

    @Test
    void customRegistration_overridesDefault() {
        ExchangeSegment segment = ExchangeSegment.BSE_FNO;
        long original = ExchangeTickSizeRegistry.tickSizePaisa(segment);

        ExchangeTickSizeRegistry.register(segment, 10L);
        assertEquals(10L, ExchangeTickSizeRegistry.tickSizePaisa(segment));

        // Restore original value to avoid polluting other tests
        ExchangeTickSizeRegistry.register(segment, original);
    }
}
