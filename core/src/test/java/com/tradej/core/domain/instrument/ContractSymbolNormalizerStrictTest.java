package com.tradej.core.domain.instrument;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContractSymbolNormalizerStrictTest {

    @Test
    void normalizeStrict_knownFuturesSymbol_normalizes() {
        String result = ContractSymbolNormalizer.normalizeStrict("NIFTY 30 JUN FUT");
        assertEquals("NIFTY 30 JUN FUT", result);
    }

    @Test
    void normalizeStrict_compactFuturesSymbol_normalizes() {
        String result = ContractSymbolNormalizer.normalizeStrict("NIFTY30JUNFUT");
        assertEquals("NIFTY 30 JUN FUT", result);
    }

    @Test
    void normalizeStrict_knownOptionSymbol_normalizes() {
        String result = ContractSymbolNormalizer.normalizeStrict("BANKNIFTY 30 JUN 30000 CE");
        assertEquals("BANKNIFTY 30 JUN 30000 CALL", result);
    }

    @Test
    void normalizeStrict_compactOptionSymbol_normalizes() {
        String result = ContractSymbolNormalizer.normalizeStrict("BANKNIFTY30JUN30000PE");
        assertEquals("BANKNIFTY 30 JUN 30000 PUT", result);
    }

    @Test
    void normalizeStrict_garbageString_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ContractSymbolNormalizer.normalizeStrict("GARBAGE123"));
    }

    @Test
    void normalizeStrict_nullString_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ContractSymbolNormalizer.normalizeStrict(null));
    }

    @Test
    void normalizeStrict_blankString_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ContractSymbolNormalizer.normalizeStrict("   "));
    }

    @Test
    void normalizeStrict_plainEquitySymbol_throws() {
        // A plain equity symbol like "SBIN" doesn't match any F&O pattern
        assertThrows(IllegalArgumentException.class,
                () -> ContractSymbolNormalizer.normalizeStrict("SBIN"));
    }

    @Test
    void normalize_nonStrict_stillReturnsUppercaseForBackwardCompat() {
        // Plain equity symbols should still be returned as uppercase (backward compat)
        String result = ContractSymbolNormalizer.normalize("sbin");
        assertEquals("SBIN", result);
    }

    @Test
    void normalize_nonStrict_garbageReturnsUppercase() {
        String result = ContractSymbolNormalizer.normalize("random_garbage");
        assertEquals("RANDOM_GARBAGE", result);
    }

    @Test
    void normalize_nonStrict_knownFuture_stillNormalizes() {
        String result = ContractSymbolNormalizer.normalize("NIFTY 30 JUN FUT");
        assertEquals("NIFTY 30 JUN FUT", result);
    }

    @Test
    void normalize_nonStrict_knownOption_stillNormalizes() {
        String result = ContractSymbolNormalizer.normalize("BANKNIFTY 30 JUN 30000 CE");
        assertEquals("BANKNIFTY 30 JUN 30000 CALL", result);
    }
}
