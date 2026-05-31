package com.tradej.broker.upstox.instrument;

import com.tradej.core.domain.value.ExchangeSegment;

/**
 * Maps Upstox {@code segment} codes to domain {@link ExchangeSegment}.
 */
public final class UpstoxSegmentMapper {
    private UpstoxSegmentMapper() {
    }

    public static ExchangeSegment fromUpstoxSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return ExchangeSegment.UNKNOWN;
        }
        return switch (segment.trim().toUpperCase()) {
            case "NSE_EQ" -> ExchangeSegment.NSE_EQ;
            case "NSE_FO" -> ExchangeSegment.NSE_FNO;
            case "BSE_EQ" -> ExchangeSegment.BSE_EQ;
            case "BSE_FO" -> ExchangeSegment.BSE_FNO;
            case "NSE_INDEX", "BSE_INDEX" -> ExchangeSegment.IDX_I;
            case "MCX_FO", "NSE_COM" -> ExchangeSegment.MCX_COMM;
            case "NCD_FO" -> ExchangeSegment.NSE_CURRENCY;
            case "BCD_FO" -> ExchangeSegment.BSE_CURRENCY;
            default -> ExchangeSegment.UNKNOWN;
        };
    }
}
