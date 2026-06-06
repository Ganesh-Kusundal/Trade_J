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

    public static ExchangeSegment fromIciciCode(String exchangeCode) {
        Objects.requireNonNull(exchangeCode, "exchangeCode");
        return switch (exchangeCode.toUpperCase()) {
            case "NSE" -> ExchangeSegment.NSE_EQ;
            case "NFO" -> ExchangeSegment.NSE_FNO;
            case "BSE" -> ExchangeSegment.BSE_EQ;
            case "BFO" -> ExchangeSegment.BSE_FNO;
            case "MCX" -> ExchangeSegment.MCX_COMM;
            case "CDS" -> ExchangeSegment.NSE_CURRENCY;
            default -> throw new IllegalArgumentException("Unknown ICICI exchange code: " + exchangeCode);
        };
    }

    public static ExchangeSegment resolveSegmentFromStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return ExchangeSegment.NSE_EQ;
        }
        String upper = stockCode.toUpperCase();
        if (upper.endsWith("-BSE") || upper.endsWith(".BSE") || upper.startsWith("BSE:")) {
            return ExchangeSegment.BSE_EQ;
        }
        if (upper.contains("FUT") || upper.contains("OPT") || upper.endsWith("-NFO") || upper.endsWith(".NFO")) {
            return ExchangeSegment.NSE_FNO;
        }
        if (upper.endsWith("-BFO") || upper.endsWith(".BFO")) {
            return ExchangeSegment.BSE_FNO;
        }
        if (upper.endsWith("-MCX") || upper.endsWith(".MCX")) {
            return ExchangeSegment.MCX_COMM;
        }
        if (upper.endsWith("-CDS") || upper.endsWith(".CDS")) {
            return ExchangeSegment.NSE_CURRENCY;
        }
        return ExchangeSegment.NSE_EQ;
    }
}
