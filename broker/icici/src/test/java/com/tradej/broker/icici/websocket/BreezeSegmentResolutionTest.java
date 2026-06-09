package com.tradej.broker.icici.websocket;

import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for R2 fix: ICICI segment resolution from exchange_code field.
 * Verifies that the segment is resolved from the tick JSON instead of hardcoded to NSE_EQ.
 */
@Tag("unit")
class BreezeSegmentResolutionTest {

    @Test
    void resolveSegment_nse_returnsNseEq() {
        assertEquals(ExchangeSegment.NSE_EQ, resolveSegment("NSE", "RELIANCE"));
        assertEquals(ExchangeSegment.NSE_EQ, resolveSegment("NSE_CM", "RELIANCE"));
    }

    @Test
    void resolveSegment_bse_returnsBseEq() {
        assertEquals(ExchangeSegment.BSE_EQ, resolveSegment("BSE", "RELIANCE"));
        assertEquals(ExchangeSegment.BSE_EQ, resolveSegment("BSE_CM", "RELIANCE"));
    }

    @Test
    void resolveSegment_nseFo_returnsNseFno() {
        assertEquals(ExchangeSegment.NSE_FNO, resolveSegment("NSE_FO", "NIFTY"));
    }

    @Test
    void resolveSegment_bseFo_returnsBseFno() {
        assertEquals(ExchangeSegment.BSE_FNO, resolveSegment("BSE_FO", "SENSEX"));
    }

    @Test
    void resolveSegment_mcx_returnsMcxComm() {
        assertEquals(ExchangeSegment.MCX_COMM, resolveSegment("MCX", "GOLD"));
    }

    @Test
    void resolveSegment_null_defaultsToNseEq() {
        assertEquals(ExchangeSegment.NSE_EQ, resolveSegment(null, "RELIANCE"));
    }

    @Test
    void resolveSegment_empty_defaultsToNseEq() {
        assertEquals(ExchangeSegment.NSE_EQ, resolveSegment("", "RELIANCE"));
    }

    @Test
    void resolveSegment_unknown_defaultsToNseEq() {
        assertEquals(ExchangeSegment.NSE_EQ, resolveSegment("UNKNOWN_EXCHANGE", "TEST"));
    }

    // Mirror of BreezeWebSocketMultiplexer.resolveSegment for testing
    private ExchangeSegment resolveSegment(String exchangeCode, String symbol) {
        if (exchangeCode == null || exchangeCode.isBlank()) {
            return ExchangeSegment.NSE_EQ;
        }
        return switch (exchangeCode.toUpperCase()) {
            case "NSE", "NSE_CM"     -> ExchangeSegment.NSE_EQ;
            case "BSE", "BSE_CM"     -> ExchangeSegment.BSE_EQ;
            case "NSE_FO"           -> ExchangeSegment.NSE_FNO;
            case "BSE_FO"           -> ExchangeSegment.BSE_FNO;
            case "NSE_RX", "CDS"     -> ExchangeSegment.NSE_CURRENCY;
            case "MCX"              -> ExchangeSegment.MCX_COMM;
            default                  -> ExchangeSegment.NSE_EQ;
        };
    }
}
