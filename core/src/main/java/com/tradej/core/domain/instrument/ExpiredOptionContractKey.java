package com.tradej.core.domain.instrument;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

import java.time.LocalDate;

public record ExpiredOptionContractKey(
        String underlying,
        ExchangeSegment segment,
        LocalDate expiry,
        long strikePaisa,
        OptionType optionType,
        String brokerInstrumentKey
) {
    public ExpiredOptionContractKey {
        underlying = ContractSymbolNormalizer.normalize(underlying);
        if (underlying.isBlank()) {
            throw new IllegalArgumentException("underlying is required");
        }
        if (segment == null) {
            throw new IllegalArgumentException("segment is required");
        }
        if (expiry == null) {
            throw new IllegalArgumentException("expiry is required");
        }
        if (optionType == null || optionType == OptionType.UNKNOWN) {
            throw new IllegalArgumentException("optionType must be CALL or PUT");
        }
        if (brokerInstrumentKey == null || brokerInstrumentKey.isBlank()) {
            throw new IllegalArgumentException("brokerInstrumentKey is required");
        }
    }
}
