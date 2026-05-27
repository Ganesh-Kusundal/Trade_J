package com.tradej.broker.dhan.instrument;

import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.Map;

public final class DhanSegmentMapper {
    private static final Map<String, ExchangeSegment> CSV_SEGMENT_TO_INTERNAL = Map.of(
            "NSE::E", ExchangeSegment.NSE_EQ,
            "NSE::D", ExchangeSegment.NSE_FNO,
            "NSE::I", ExchangeSegment.IDX_I,
            "BSE::E", ExchangeSegment.BSE_EQ,
            "BSE::D", ExchangeSegment.BSE_FNO,
            "BSE::I", ExchangeSegment.IDX_I,
            "MCX::M", ExchangeSegment.MCX_COMM,
            "CDS::D", ExchangeSegment.NSE_CURRENCY
    );
    private static final Map<String, ExchangeSegment> VALUE_TO_INTERNAL = Map.ofEntries(
            Map.entry("NSE", ExchangeSegment.NSE_EQ),
            Map.entry("NSE_EQ", ExchangeSegment.NSE_EQ),
            Map.entry("BSE", ExchangeSegment.BSE_EQ),
            Map.entry("BSE_EQ", ExchangeSegment.BSE_EQ),
            Map.entry("NFO", ExchangeSegment.NSE_FNO),
            Map.entry("NSE_FNO", ExchangeSegment.NSE_FNO),
            Map.entry("BFO", ExchangeSegment.BSE_FNO),
            Map.entry("BSE_FNO", ExchangeSegment.BSE_FNO),
            Map.entry("INDEX", ExchangeSegment.IDX_I),
            Map.entry("IDX_I", ExchangeSegment.IDX_I),
            Map.entry("MCX", ExchangeSegment.MCX_COMM),
            Map.entry("MCX_COMM", ExchangeSegment.MCX_COMM),
            Map.entry("CDS", ExchangeSegment.NSE_CURRENCY),
            Map.entry("NSE_CURRENCY", ExchangeSegment.NSE_CURRENCY),
            Map.entry("BSE_CURRENCY", ExchangeSegment.BSE_CURRENCY)
    );

    private DhanSegmentMapper() {
    }

    public static ExchangeSegment fromCsv(String exchangeId, String segmentCode) {
        return CSV_SEGMENT_TO_INTERNAL.getOrDefault(
                normalize(exchangeId) + "::" + normalize(segmentCode),
                ExchangeSegment.UNKNOWN
        );
    }

    public static ExchangeSegment fromValue(String value) {
        return VALUE_TO_INTERNAL.getOrDefault(normalize(value), ExchangeSegment.fromCode(normalize(value)));
    }

    public static Exchange toExchange(String value) {
        ExchangeSegment segment = fromValue(value);
        return segment == ExchangeSegment.UNKNOWN ? Exchange.UNKNOWN : segment.venueExchange();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
