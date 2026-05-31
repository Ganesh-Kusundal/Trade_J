package com.tradej.broker.dhan.instrument;

import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.value.OptionType;

import java.time.LocalDate;

final class DhanSymbolNormalizer {
    private DhanSymbolNormalizer() {
    }

    static String canonicalOptionSymbol(String underlying, LocalDate expiry, Long strikePricePaisa, OptionType optionType) {
        return ContractSymbolNormalizer.getCanonicalSymbol(underlying, expiry, strikePricePaisa, optionType, true);
    }

    static String canonicalFutureSymbol(String underlying, LocalDate expiry) {
        return ContractSymbolNormalizer.getCanonicalSymbol(underlying, expiry, null, OptionType.UNKNOWN, false);
    }

    static String normalize(String value) {
        return ContractSymbolNormalizer.normalize(value);
    }

    static String stripped(String value) {
        return ContractSymbolNormalizer.stripped(value);
    }

    static String extractFutureUnderlying(String tradingSymbol) {
        return ContractSymbolNormalizer.extractFutureUnderlying(tradingSymbol);
    }
}
