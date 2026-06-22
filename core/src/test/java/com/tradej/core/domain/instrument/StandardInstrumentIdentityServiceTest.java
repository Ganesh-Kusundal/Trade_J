package com.tradej.core.domain.instrument;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class StandardInstrumentIdentityServiceTest {

    private final StandardInstrumentIdentityService identity = StandardInstrumentIdentityService.INSTANCE;

    @Test
    void keyNormalizesSymbolAndPreservesSegment() {
        InstrumentKey key = identity.key("sbin", ExchangeSegment.NSE_EQ);

        assertEquals("SBIN", key.symbol());
        assertEquals(ExchangeSegment.NSE_EQ, key.exchangeSegment());
    }

    @Test
    void optionContractsUseCanonicalForm() {
        InstrumentKey key = identity.fno("nifty26may30750ce");

        assertEquals("NIFTY 26 MAY 30750 CALL", key.symbol());
        assertEquals(ExchangeSegment.NSE_FNO, key.exchangeSegment());
    }

    @Test
    void convenienceMethodsEncodeDefaultSegments() {
        assertEquals(ExchangeSegment.NSE_EQ, identity.equity("reliance").exchangeSegment());
        assertEquals(ExchangeSegment.IDX_I, identity.index("nifty").exchangeSegment());
        assertEquals(ExchangeSegment.MCX_COMM, identity.commodity("goldm").exchangeSegment());
    }

    @Test
    void nullSegmentIsRejected() {
        assertThrows(NullPointerException.class, () -> identity.key("SBIN", null));
    }
}
