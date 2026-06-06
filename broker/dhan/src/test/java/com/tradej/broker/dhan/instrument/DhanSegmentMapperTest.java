package com.tradej.broker.dhan.instrument;

import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class DhanSegmentMapperTest {

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
    void toWireValueIsStableAcrossCalls() {
        // Verifies the mapping is a pure function — calling it twice yields the same value.
        String first = DhanSegmentMapper.toWireValue(ExchangeSegment.NSE_FNO);
        String second = DhanSegmentMapper.toWireValue(ExchangeSegment.NSE_FNO);
        assertEquals(first, second);
    }

    @Test
    void fromValueRoundTripsThroughToWireValue() {
        // Inbound: Dhan wire value "NSE_FNO" → canonical NSE_FNO
        // Outbound: canonical NSE_FNO → wire value "NSE_FNO"
        // Together they form a stable round-trip.
        for (ExchangeSegment segment : ExchangeSegment.values()) {
            if (segment == ExchangeSegment.UNKNOWN) continue;
            String wire = DhanSegmentMapper.toWireValue(segment);
            assertEquals(segment, DhanSegmentMapper.fromValue(wire),
                    "Round-trip failed for " + segment + " via wire value " + wire);
        }
    }
}
