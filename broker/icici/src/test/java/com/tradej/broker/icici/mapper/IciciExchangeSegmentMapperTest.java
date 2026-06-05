package com.tradej.broker.icici.mapper;

import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class IciciExchangeSegmentMapperTest {

    @Test
    void mapsCanonicalSegmentsToBreezeExchangeCodes() {
        assertEquals("NSE", IciciExchangeSegmentMapper.toIciciCode(ExchangeSegment.NSE_EQ));
        assertEquals("NFO", IciciExchangeSegmentMapper.toIciciCode(ExchangeSegment.NSE_FNO));
        assertEquals("BSE", IciciExchangeSegmentMapper.toIciciCode(ExchangeSegment.BSE_EQ));
        assertEquals("BFO", IciciExchangeSegmentMapper.toIciciCode(ExchangeSegment.BSE_FNO));
        assertEquals("MCX", IciciExchangeSegmentMapper.toIciciCode(ExchangeSegment.MCX_COMM));
        assertEquals("CDS", IciciExchangeSegmentMapper.toIciciCode(ExchangeSegment.NSE_CURRENCY));
        assertEquals("CDS", IciciExchangeSegmentMapper.toIciciCode(ExchangeSegment.BSE_CURRENCY));
    }

    @Test
    void rejectsNullSegment() {
        assertThrows(NullPointerException.class, () -> IciciExchangeSegmentMapper.toIciciCode(null));
    }

    @Test
    void rejectsUnknownSegment() {
        assertThrows(IllegalArgumentException.class,
                () -> IciciExchangeSegmentMapper.toIciciCode(ExchangeSegment.UNKNOWN));
    }
}
