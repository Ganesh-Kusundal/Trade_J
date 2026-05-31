package com.tradej.broker.api.port;

import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.List;

public interface InstrumentResolver {
    Instrument resolve(InstrumentKey key);

    Instrument getBySymbol(InstrumentKey key);

    default Instrument resolveNormalized(String symbol, ExchangeSegment exchangeSegment) {
        InstrumentKey key = InstrumentKey.of(symbol, exchangeSegment);
        Instrument instrument = getBySymbol(key);
        if (instrument != null) {
            return instrument;
        }
        return requireDefinition(key);
    }

    default String toCanonicalSymbol(String symbol, ExchangeSegment exchangeSegment) {
        Instrument instrument = getBySymbol(InstrumentKey.of(symbol, exchangeSegment));
        if (instrument != null) {
            return instrument.canonicalSymbol();
        }
        return ContractSymbolNormalizer.normalize(symbol);
    }

    List<Instrument> allInstruments();

    /**
     * Resolve a security ID (broker-specific identifier) to a domain Instrument.
     *
     * @param securityId the broker-specific security identifier
     * @return the resolved Instrument
     * @throws IllegalArgumentException if the security ID is unknown
     */
    Instrument resolveBySecurityId(String securityId);

    /**
     * Resolve an InstrumentKey to a domain Instrument, throwing if not found.
     *
     * @param key the instrument key to resolve
     * @return the resolved Instrument
     * @throws IllegalArgumentException if the instrument cannot be resolved
     */
    Instrument requireDefinition(InstrumentKey key);

    /**
     * Resolve an arbitrary broker SDK payload to a domain Instrument.
     *
     * @param payload the broker SDK payload object
     * @return the resolved Instrument
     * @throws IllegalArgumentException if the payload cannot be resolved
     */
    Instrument resolvePayload(Object payload);

    /**
     * Returns whether the instrument catalog has been populated.
     */
    boolean isLoaded();

    /**
     * Returns the number of instruments currently in the catalog.
     */
    int catalogSize();
}
