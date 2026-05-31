package com.tradej.broker.upstox.instrument;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.tradej.core.domain.value.ExchangeSegment;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class UpstoxSegmentMapperTest {

    @Test
    void mapsKnownUpstoxSegments() {
        assertEquals(ExchangeSegment.NSE_EQ, UpstoxSegmentMapper.fromUpstoxSegment("NSE_EQ"));
        assertEquals(ExchangeSegment.NSE_FNO, UpstoxSegmentMapper.fromUpstoxSegment("NSE_FO"));
        assertEquals(ExchangeSegment.IDX_I, UpstoxSegmentMapper.fromUpstoxSegment("NSE_INDEX"));
        assertEquals(ExchangeSegment.MCX_COMM, UpstoxSegmentMapper.fromUpstoxSegment("MCX_FO"));
    }
}
