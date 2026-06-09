package com.tradej.broker.dhan.instrument;

import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class DhanSegmentMapperTest {

    private static final Set<ExchangeSegment> NON_DHAN_SEGMENTS = Set.of(
            ExchangeSegment.UNKNOWN,
            ExchangeSegment.CRYPTO_SPOT,
            ExchangeSegment.CRYPTO_FUTURES,
            ExchangeSegment.FX_SPOT,
            ExchangeSegment.US_EQUITY
    );

    @Test
    void toWireValueReturnsCanonicalNameForAllSupportedSegments() {
        assertEquals("NSE_EQ", DhanSegmentMapper.toWireValue(ExchangeSegment.NSE_EQ));
        assertEquals("NSE_FNO", DhanSegmentMapper.toWireValue(ExchangeSegment.NSE_FNO));
        assertEquals("BSE_EQ", DhanSegmentMapper.toWireValue(ExchangeSegment.BSE_EQ));
        assertEquals("BSE_FNO", DhanSegmentMapper.toWireValue(ExchangeSegment.BSE_FNO));
        assertEquals("IDX_I", DhanSegmentMapper.toWireValue(ExchangeSegment.IDX_I));
        assertEquals("MCX_COMM", DhanSegmentMapper.toWireValue(ExchangeSegment.MCX_COMM));
        assertEquals("NSE_CURRENCY", DhanSegmentMapper.toWireValue(ExchangeSegment.NSE_CURRENCY));
        assertEquals("BSE_CURRENCY", DhanSegmentMapper.toWireValue(ExchangeSegment.BSE_CURRENCY));
    }

    @Test
    void toWireValueRejectsNullSegment() {
        assertThrows(IllegalArgumentException.class, () -> DhanSegmentMapper.toWireValue(null));
    }

    @Test
    void toWireValueRejectsUnknownSegment() {
        assertThrows(IllegalArgumentException.class, () -> DhanSegmentMapper.toWireValue(ExchangeSegment.UNKNOWN));
    }

    @Test
    void toWireValueRejectsUnsupportedSegments() {
        assertThrows(IllegalArgumentException.class, () -> DhanSegmentMapper.toWireValue(ExchangeSegment.CRYPTO_SPOT));
        assertThrows(IllegalArgumentException.class, () -> DhanSegmentMapper.toWireValue(ExchangeSegment.CRYPTO_FUTURES));
        assertThrows(IllegalArgumentException.class, () -> DhanSegmentMapper.toWireValue(ExchangeSegment.FX_SPOT));
        assertThrows(IllegalArgumentException.class, () -> DhanSegmentMapper.toWireValue(ExchangeSegment.US_EQUITY));
    }

    @Test
    void toWireValueIsStableAcrossCalls() {
        String first = DhanSegmentMapper.toWireValue(ExchangeSegment.NSE_FNO);
        String second = DhanSegmentMapper.toWireValue(ExchangeSegment.NSE_FNO);
        assertEquals(first, second);
    }

    @Test
    void fromValueRoundTripsThroughToWireValue() {
        for (ExchangeSegment segment : ExchangeSegment.values()) {
            if (NON_DHAN_SEGMENTS.contains(segment)) continue;
            String wire = DhanSegmentMapper.toWireValue(segment);
            assertEquals(segment, DhanSegmentMapper.fromValue(wire),
                    "Round-trip failed for " + segment + " via wire value " + wire);
        }
    }
}
