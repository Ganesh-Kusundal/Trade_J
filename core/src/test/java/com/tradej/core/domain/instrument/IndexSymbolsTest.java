package com.tradej.core.domain.instrument;

import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class IndexSymbolsTest {

    @Test
    void canonicalizeAcceptsCanonicalNamesUnchanged() {
        assertEquals(IndexSymbols.NIFTY, IndexSymbols.canonicalize("NIFTY"));
        assertEquals(IndexSymbols.NIFTY, IndexSymbols.canonicalize("nifty"));
        assertEquals(IndexSymbols.NIFTY, IndexSymbols.canonicalize("  NIFTY  "));
    }

    @Test
    void canonicalizeMapsBrokerAliasesToCanonicalNseNames() {
        assertEquals(IndexSymbols.NIFTY_BANK, IndexSymbols.canonicalize("BANKNIFTY"));
        assertEquals(IndexSymbols.NIFTY_BANK, IndexSymbols.canonicalize("banknifty"));
        assertEquals(IndexSymbols.NIFTY_FIN_SERVICE, IndexSymbols.canonicalize("FINNIFTY"));
        assertEquals(IndexSymbols.NIFTY_MID_SELECT, IndexSymbols.canonicalize("MIDCPNIFTY"));
    }

    @Test
    void canonicalizeLeavesCashEquitiesAndFuturesAlone() {
        assertEquals("RELIANCE", IndexSymbols.canonicalize("RELIANCE"));
        assertEquals("SBIN", IndexSymbols.canonicalize("sbin"));
        assertEquals("BANKNIFTY 30 JUN 30000 CALL",
                IndexSymbols.canonicalize("BANKNIFTY 30 JUN 30000 CALL"));
    }

    @Test
    void canonicalizeReturnsEmptyForNullOrBlank() {
        assertEquals("", IndexSymbols.canonicalize(null));
        assertEquals("", IndexSymbols.canonicalize(""));
        assertEquals("", IndexSymbols.canonicalize("   "));
    }

    @Test
    void isIndexUnderlyingReturnsTrueForCanonicalIndexNames() {
        assertTrue(IndexSymbols.isIndexUnderlying("NIFTY"));
        assertTrue(IndexSymbols.isIndexUnderlying("NIFTY BANK"));
        assertTrue(IndexSymbols.isIndexUnderlying("NIFTY FIN SERVICE"));
        assertTrue(IndexSymbols.isIndexUnderlying("NIFTY MID SELECT"));
        assertTrue(IndexSymbols.isIndexUnderlying("SENSEX"));
        assertTrue(IndexSymbols.isIndexUnderlying("BANKEX"));
    }

    @Test
    void isIndexUnderlyingReturnsTrueForBrokerAliases() {
        assertTrue(IndexSymbols.isIndexUnderlying("BANKNIFTY"));
        assertTrue(IndexSymbols.isIndexUnderlying("FINNIFTY"));
        assertTrue(IndexSymbols.isIndexUnderlying("MIDCPNIFTY"));
    }

    @Test
    void isIndexUnderlyingReturnsTrueForIndexOptionAndFutureContracts() {
        // "NIFTY 30 JUN 30000 CALL" is an option whose underlying is NIFTY → index
        assertTrue(IndexSymbols.isIndexUnderlying("NIFTY 30 JUN 30000 CALL"));
        assertTrue(IndexSymbols.isIndexUnderlying("NIFTY BANK 30 JUN 30000 PUT"));
        assertTrue(IndexSymbols.isIndexUnderlying("NIFTY 30 JUN FUT"));
    }

    @Test
    void isIndexUnderlyingReturnsFalseForCashEquitiesAndStockOptions() {
        assertFalse(IndexSymbols.isIndexUnderlying("RELIANCE"));
        assertFalse(IndexSymbols.isIndexUnderlying("TCS"));
        assertFalse(IndexSymbols.isIndexUnderlying("SBIN"));
        // Stock option underlying is RELIANCE, not an index
        assertFalse(IndexSymbols.isIndexUnderlying("RELIANCE 30 JUN 30000 CALL"));
    }

    @Test
    void isIndexUnderlyingReturnsFalseForNullOrBlank() {
        assertFalse(IndexSymbols.isIndexUnderlying(null));
        assertFalse(IndexSymbols.isIndexUnderlying(""));
        assertFalse(IndexSymbols.isIndexUnderlying("   "));
    }

    @Test
    void defaultSegmentReturnsIdxIForSpotIndexUnderlyings() {
        assertEquals(ExchangeSegment.IDX_I, IndexSymbols.defaultSegment("NIFTY"));
        assertEquals(ExchangeSegment.IDX_I, IndexSymbols.defaultSegment("NIFTY BANK"));
        assertEquals(ExchangeSegment.IDX_I, IndexSymbols.defaultSegment("BANKNIFTY"));
        assertEquals(ExchangeSegment.IDX_I, IndexSymbols.defaultSegment("SENSEX"));
        assertEquals(ExchangeSegment.IDX_I, IndexSymbols.defaultSegment("BANKEX"));
        assertEquals(ExchangeSegment.IDX_I, IndexSymbols.defaultSegment("NIFTY FIN SERVICE"));
    }

    @Test
    void defaultSegmentRoutesContractsToFno() {
        // Contract symbols (option or future) are checked before "is index underlying"
        // because an index option like "NIFTY 30 JUN 30000 CALL" matches BOTH — but the
        // contract's actual market segment is NSE_FNO, not IDX_I.
        assertEquals(ExchangeSegment.NSE_FNO, IndexSymbols.defaultSegment("NIFTY 30 JUN 30000 CALL"));
        assertEquals(ExchangeSegment.NSE_FNO, IndexSymbols.defaultSegment("NIFTY BANK 30 JUN 30000 PUT"));
        assertEquals(ExchangeSegment.NSE_FNO, IndexSymbols.defaultSegment("NIFTY 30 JUN FUT"));
        assertEquals(ExchangeSegment.NSE_FNO, IndexSymbols.defaultSegment("RELIANCE 30 JUN 3000 CALL"));
        assertEquals(ExchangeSegment.NSE_FNO, IndexSymbols.defaultSegment("SBIN 28 DEC FUT"));
    }

    @Test
    void defaultSegmentReturnsNseEqForCashEquitiesAndMalformed() {
        assertEquals(ExchangeSegment.NSE_EQ, IndexSymbols.defaultSegment("RELIANCE"));
        assertEquals(ExchangeSegment.NSE_EQ, IndexSymbols.defaultSegment("TCS"));
        assertEquals(ExchangeSegment.NSE_EQ, IndexSymbols.defaultSegment(null));
        assertEquals(ExchangeSegment.NSE_EQ, IndexSymbols.defaultSegment(""));
        assertEquals(ExchangeSegment.NSE_EQ, IndexSymbols.defaultSegment("   "));
        // Malformed (looks index-ish but isn't a valid contract) still falls through to
        // the isIndexUnderlying check first — but "NIFTYBANK" with no space isn't an
        // index name either, so it falls through to NSE_EQ.
        assertEquals(ExchangeSegment.NSE_EQ, IndexSymbols.defaultSegment("NIFTYBANK"));
    }

    @Test
    void canonicalIndexSetReturnsDeterministicOrderedList() {
        assertEquals(
                java.util.List.of(
                        IndexSymbols.NIFTY_MID_SELECT,
                        IndexSymbols.NIFTY_FIN_SERVICE,
                        IndexSymbols.NIFTY_BANK,
                        IndexSymbols.NIFTY,
                        IndexSymbols.SENSEX,
                        IndexSymbols.BANKEX
                ),
                new java.util.ArrayList<>(IndexSymbols.canonicalIndexSet())
        );
    }

    @Test
    void instrumentsFactoryExposesAllCanonicalNseIndices() {
        // Every constant in IndexSymbols must have a corresponding Instruments factory,
        // otherwise the public API is incomplete (callers would have to reach into
        // IndexSymbols for SENSEX/BANKEX but not for NIFTY/BANKNIFTY).
        assertEquals(IndexSymbols.NIFTY, Instruments.nifty().symbol());
        assertEquals(ExchangeSegment.IDX_I, Instruments.nifty().exchangeSegment());
        assertEquals(IndexSymbols.NIFTY_BANK, Instruments.bankNifty().symbol());
        assertEquals(IndexSymbols.NIFTY_FIN_SERVICE, Instruments.finNifty().symbol());
        assertEquals(IndexSymbols.NIFTY_MID_SELECT, Instruments.midcpNifty().symbol());
        assertEquals(IndexSymbols.SENSEX, Instruments.sensex().symbol());
        assertEquals(ExchangeSegment.IDX_I, Instruments.sensex().exchangeSegment());
        assertEquals(IndexSymbols.BANKEX, Instruments.bankex().symbol());
        assertEquals(ExchangeSegment.IDX_I, Instruments.bankex().exchangeSegment());
    }
}
