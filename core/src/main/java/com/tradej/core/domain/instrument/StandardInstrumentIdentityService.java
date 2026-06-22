package com.tradej.core.domain.instrument;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.InstrumentIdentityService;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.Objects;

/**
 * Default instrument identity rules shared by live, replay, backtest, app, and CLI.
 */
public final class StandardInstrumentIdentityService implements InstrumentIdentityService {

    public static final StandardInstrumentIdentityService INSTANCE = new StandardInstrumentIdentityService();

    private StandardInstrumentIdentityService() {
    }

    @Override
    public String canonicalSymbol(String rawSymbol) {
        return ContractSymbolNormalizer.normalize(rawSymbol);
    }

    @Override
    public InstrumentKey key(String rawSymbol, ExchangeSegment exchangeSegment) {
        return new InstrumentKey(
                canonicalSymbol(rawSymbol),
                Objects.requireNonNull(exchangeSegment, "exchangeSegment")
        );
    }
}
