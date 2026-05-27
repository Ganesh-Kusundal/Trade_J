package com.tradej.broker.dhan.instrument;

import com.tradej.core.domain.value.OptionType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class DhanSymbolNormalizer {
    private DhanSymbolNormalizer() {
    }

    static String canonicalOptionSymbol(String underlying, LocalDate expiry, Long strikePricePaisa, OptionType optionType) {
        return SymbolNormalizationEngine.getCanonicalSymbol(underlying, expiry, strikePricePaisa, optionType, true);
    }

    static String canonicalFutureSymbol(String underlying, LocalDate expiry) {
        return SymbolNormalizationEngine.getCanonicalSymbol(underlying, expiry, null, OptionType.UNKNOWN, false);
    }

    static String stripped(String value) {
        return SymbolNormalizationEngine.stripped(value);
    }

    static String extractFutureUnderlying(String tradingSymbol) {
        return SymbolNormalizationEngine.extractFutureUnderlying(tradingSymbol);
    }
}
