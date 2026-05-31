package com.tradej.core.domain.instrument;

import com.tradej.core.domain.model.Instrument;

/**
 * Matches user/broker symbol aliases against loaded {@link Instrument} records.
 */
public final class ContractSymbolMatcher {
    private ContractSymbolMatcher() {
    }

    public static boolean matches(Instrument instrument, String rawSymbol) {
        if (instrument == null || rawSymbol == null || rawSymbol.isBlank()) {
            return false;
        }
        String normalized = ContractSymbolNormalizer.normalize(rawSymbol);
        return instrument.canonicalSymbol().equalsIgnoreCase(normalized)
                || instrument.canonicalSymbol().equalsIgnoreCase(rawSymbol)
                || instrument.symbol().equalsIgnoreCase(rawSymbol)
                || instrument.symbol().equalsIgnoreCase(normalized)
                || ContractSymbolNormalizer.stripped(instrument.canonicalSymbol())
                .equals(ContractSymbolNormalizer.stripped(rawSymbol));
    }
}
