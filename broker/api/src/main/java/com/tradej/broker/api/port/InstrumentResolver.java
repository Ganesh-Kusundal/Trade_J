package com.tradej.broker.api.port;

import com.tradej.broker.api.annotation.BrokerInternal;
import com.tradej.core.domain.instrument.StandardInstrumentIdentityService;
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
        return StandardInstrumentIdentityService.INSTANCE.canonicalSymbol(symbol);
    }

    List<Instrument> allInstruments();

    /**
     * Resolve a security ID (broker-specific identifier) to a domain Instrument.
     *
     * <p><b>Broker-internal:</b> the {@code securityId} is a broker-specific concept
     * (e.g. Dhan's numeric {@code securityId}, Upstox's instrument key string, ICICI's
     * {@code stock_code}). Callers outside {@code broker.*} modules should prefer the
     * canonical {@link #resolve(InstrumentKey)} / {@link #requireDefinition(InstrumentKey)}
     * / {@link #resolveNormalized(String, ExchangeSegment)} surface. This method exists
     * so the broker-gateway can bridge inbound broker events (e.g. WebSocket packets that
     * carry only a security ID) to the canonical domain.
     *
     * @param securityId the broker-specific security identifier
     * @return the resolved Instrument
     * @throws IllegalArgumentException if the security ID is unknown
     */
    @BrokerInternal(reason = "Inbound bridge from broker wire codes (securityId, stock_code) to canonical domain.")
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
     * <p><b>Broker-internal:</b> the {@code payload} is an opaque broker SDK object
     * (e.g. Dhan's instrument master row, Upstox's {@code Instrument} record, ICICI's
     * Breeze response). The caller must know the broker type to know what payload shape
     * to pass in. This method exists so the broker modules can normalise inbound SDK
     * objects without duplicating the resolution logic; callers outside {@code broker.*}
     * modules should not invoke it.
     *
     * @param payload the broker SDK payload object
     * @return the resolved Instrument
     * @throws IllegalArgumentException if the payload cannot be resolved
     */
    @BrokerInternal(reason = "Outbound bridge from broker SDK payloads to canonical domain.")
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
