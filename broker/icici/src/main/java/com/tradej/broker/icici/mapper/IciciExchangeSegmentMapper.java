package com.tradej.broker.icici.mapper;

import com.tradej.core.domain.value.ExchangeSegment;

import java.util.List;
import java.util.Objects;

/**
 * Maps canonical {@link ExchangeSegment} values to ICICI Breeze {@code exchange_code} wire values.
 */
public final class IciciExchangeSegmentMapper {

    private static final List<String> ORDER_BOOK_EXCHANGES = List.of("NSE", "NFO", "BSE", "BFO", "MCX", "CDS");

    private IciciExchangeSegmentMapper() {
    }

    public static String toIciciCode(ExchangeSegment segment) {
        Objects.requireNonNull(segment, "exchangeSegment");
        return switch (segment) {
            case NSE_EQ -> "NSE";
            case NSE_FNO -> "NFO";
            case BSE_EQ -> "BSE";
            case BSE_FNO -> "BFO";
            case MCX_COMM -> "MCX";
            case NSE_CURRENCY, BSE_CURRENCY -> "CDS";
            case IDX_I -> "NSE";
            default -> throw new IllegalArgumentException("Unsupported ICICI exchange segment: " + segment);
        };
    }

    public static List<String> supportedOrderBookExchangeCodes() {
        return ORDER_BOOK_EXCHANGES;
    }
}
