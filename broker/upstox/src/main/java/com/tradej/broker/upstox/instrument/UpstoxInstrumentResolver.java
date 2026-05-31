package com.tradej.broker.upstox.instrument;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Instrument resolver for Upstox.
 * <p>
 * Maps between Upstox {@code instrument_key} ({@code NSE_EQ|INE...}),
 * domain {@link InstrumentKey}, and canonical {@link Instrument} records.
 */
public final class UpstoxInstrumentResolver implements InstrumentResolver {

    private final ConcurrentMap<String, Instrument> byInstrumentKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instrument> byDomainKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> domainKeyToInstrumentKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> tokenToInstrumentKey = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public void register(UpstoxInstrumentDefinition def) {
        Instrument instrument = def.toInstrument();
        byInstrumentKey.put(def.instrumentKey(), instrument);
        String domainKey = domainKey(def.toInstrumentKey());
        byDomainKey.put(domainKey, instrument);
        domainKeyToInstrumentKey.put(domainKey, def.instrumentKey());
        if (def.instrumentToken() > 0) {
            tokenToInstrumentKey.put(def.instrumentToken(), def.instrumentKey());
        }
        loaded = true;
    }

    /**
     * Returns the Upstox {@code instrument_key} for API calls.
     */
    public String requireInstrumentKey(InstrumentKey key) {
        String domainKey = domainKey(key);
        String instrumentKey = domainKeyToInstrumentKey.get(domainKey);
        if (instrumentKey != null) {
            return instrumentKey;
        }
        Instrument resolved = resolve(key);
        if (resolved != null) {
            instrumentKey = domainKeyToInstrumentKey.get(domainKey(resolved.key()));
            if (instrumentKey != null) {
                return instrumentKey;
            }
        }
        throw new IllegalArgumentException(
                "No Upstox instrument_key for " + key + ". Load the instrument catalog first.");
    }

    public String resolveSymbol(long instrumentToken) {
        String instrumentKey = tokenToInstrumentKey.get(instrumentToken);
        if (instrumentKey == null) {
            throw new IllegalArgumentException("Unknown instrument token: " + instrumentToken);
        }
        Instrument instrument = byInstrumentKey.get(instrumentKey);
        if (instrument == null) {
            throw new IllegalArgumentException("Unknown instrument token: " + instrumentToken);
        }
        return instrument.symbol();
    }

    public ExchangeSegment resolveSegment(long instrumentToken) {
        String instrumentKey = tokenToInstrumentKey.get(instrumentToken);
        if (instrumentKey == null) {
            throw new IllegalArgumentException("Unknown instrument token: " + instrumentToken);
        }
        Instrument instrument = byInstrumentKey.get(instrumentKey);
        if (instrument == null) {
            throw new IllegalArgumentException("Unknown instrument token: " + instrumentToken);
        }
        return instrument.exchangeSegment();
    }

    @Override
    public Instrument resolve(InstrumentKey key) {
        Instrument inst = byDomainKey.get(domainKey(key));
        if (inst != null) {
            return inst;
        }
        String normalized = ContractSymbolNormalizer.normalize(key.symbol());
        return byDomainKey.get(domainKey(new InstrumentKey(normalized, key.exchangeSegment())));
    }

    @Override
    public Instrument getBySymbol(InstrumentKey key) {
        return resolve(key);
    }

    @Override
    public List<Instrument> allInstruments() {
        return List.copyOf(new ArrayList<>(byInstrumentKey.values()));
    }

    @Override
    public Instrument resolveBySecurityId(String securityId) {
        Instrument inst = byInstrumentKey.get(securityId);
        if (inst == null) {
            throw new IllegalArgumentException("Unknown Upstox instrument_key: " + securityId);
        }
        return inst;
    }

    @Override
    public Instrument requireDefinition(InstrumentKey key) {
        Instrument inst = resolve(key);
        if (inst == null) {
            throw new IllegalArgumentException("No instrument definition for " + key);
        }
        return inst;
    }

    @Override
    public Instrument resolvePayload(Object payload) {
        throw new UnsupportedOperationException("SDK payload resolution not supported (raw HTTP mode)");
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public int catalogSize() {
        return byInstrumentKey.size();
    }

    private static String domainKey(InstrumentKey key) {
        return ContractSymbolNormalizer.normalize(key.symbol()) + "|" + key.exchangeSegment().name();
    }
}
