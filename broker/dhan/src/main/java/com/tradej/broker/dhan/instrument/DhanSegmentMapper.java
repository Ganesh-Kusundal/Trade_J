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

    /**
     * Canonical {@link ExchangeSegment} → Dhan REST wire code.
     * <p>Dhan's REST API accepts the canonical segment names directly (e.g. {@code NSE_FNO},
     * {@code MCX_COMM}, {@code IDX_I}). Centralising the mapping here means callers must
     * not pass {@code ExchangeSegment.name()} directly — the contract is that any
     * canonical → wire translation flows through this method, so future Dhan wire-code
     * drift can be patched in one place.
     */
    private static final Map<ExchangeSegment, String> INTERNAL_TO_WIRE = Map.ofEntries(
            Map.entry(ExchangeSegment.NSE_EQ, "NSE_EQ"),
            Map.entry(ExchangeSegment.NSE_FNO, "NSE_FNO"),
            Map.entry(ExchangeSegment.BSE_EQ, "BSE_EQ"),
            Map.entry(ExchangeSegment.BSE_FNO, "BSE_FNO"),
            Map.entry(ExchangeSegment.IDX_I, "IDX_I"),
            Map.entry(ExchangeSegment.MCX_COMM, "MCX_COMM"),
            Map.entry(ExchangeSegment.NSE_CURRENCY, "NSE_CURRENCY"),
            Map.entry(ExchangeSegment.BSE_CURRENCY, "BSE_CURRENCY")
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

    /**
     * Returns the Dhan REST wire code for a canonical {@link ExchangeSegment}.
     * <p>Currently the wire codes coincide with the canonical enum names, but all
     * Dhan REST callers must route through this method so the mapping remains a
     * single point of change if Dhan ever renames a segment on its API.
     *
     * @throws IllegalArgumentException when the segment is {@link ExchangeSegment#UNKNOWN}
     *         or otherwise not supported by the Dhan REST surface.
     */
    public static String toWireValue(ExchangeSegment segment) {
        if (segment == null) {
            throw new IllegalArgumentException("segment is null");
        }
        String wire = INTERNAL_TO_WIRE.get(segment);
        if (wire == null) {
            throw new IllegalArgumentException(
                    "No Dhan REST wire code for canonical segment: " + segment);
        }
        return wire;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
