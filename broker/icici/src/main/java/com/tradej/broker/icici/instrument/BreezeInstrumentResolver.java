package com.tradej.broker.icici.instrument;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BreezeInstrumentResolver implements InstrumentResolver {
    private final Map<String, BreezeInstrumentDefinition> definitions = new ConcurrentHashMap<>();
    private final BreezeInstrumentLoader loader;
    private volatile boolean loaded;

    public BreezeInstrumentResolver() {
        this(new BreezeInstrumentLoader());
    }

    public BreezeInstrumentResolver(BreezeInstrumentLoader loader) {
        this.loader = loader;
    }

    public void loadCatalog(Path catalogPath) {
        try {
            definitions.clear();
            definitions.putAll(loader.loadFromPath(catalogPath));
            loaded = !definitions.isEmpty();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load ICICI instrument catalog from " + catalogPath, ex);
        }
    }

    public void loadFromRemote() {
        try {
            definitions.clear();
            definitions.putAll(loader.loadFromRemote());
            loaded = !definitions.isEmpty();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to download ICICI SecurityMaster", ex);
        }
    }

    public BreezeInstrumentDefinition requireBreezeDefinition(InstrumentKey instrumentKey) {
        BreezeInstrumentDefinition definition = definitions.get(
                BreezeInstrumentLoader.key(instrumentKey.symbol(), instrumentKey.exchangeSegment()));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown ICICI instrument: " + instrumentKey);
        }
        return definition;
    }

    @Override
    public Instrument resolveNormalized(String symbol, ExchangeSegment exchangeSegment) {
        InstrumentKey rawKey = new InstrumentKey(symbol, exchangeSegment);
        Instrument instrument = getBySymbol(rawKey);
        if (instrument != null) {
            return instrument;
        }
        InstrumentKey normalizedKey = InstrumentKey.of(symbol, exchangeSegment);
        if (!normalizedKey.equals(rawKey)) {
            instrument = getBySymbol(normalizedKey);
            if (instrument != null) {
                return instrument;
            }
        }
        String upper = ContractSymbolNormalizer.normalize(symbol);
        if (!upper.equalsIgnoreCase(symbol)) {
            instrument = getBySymbol(new InstrumentKey(upper, exchangeSegment));
            if (instrument != null) {
                return instrument;
            }
        }
        return requireDefinition(rawKey);
    }

    @Override
    public Instrument resolve(InstrumentKey key) {
        return requireDefinition(key);
    }

    @Override
    public Instrument getBySymbol(InstrumentKey key) {
        BreezeInstrumentDefinition definition = definitions.get(
                BreezeInstrumentLoader.key(key.symbol(), key.exchangeSegment()));
        return definition == null ? null : definition.toInstrument();
    }

    @Override
    public Instrument resolveBySecurityId(String securityId) {
        for (BreezeInstrumentDefinition definition : definitions.values()) {
            if (definition.token().equals(securityId) || definition.scriptCode().equals(securityId)) {
                return definition.toInstrument();
            }
        }
        throw new IllegalArgumentException("Unknown ICICI security id: " + securityId);
    }

    @Override
    public Instrument requireDefinition(InstrumentKey key) {
        return requireBreezeDefinition(key).toInstrument();
    }

    @Override
    public Instrument resolvePayload(Object payload) {
        if (payload instanceof String scriptCode) {
            for (BreezeInstrumentDefinition definition : definitions.values()) {
                if (definition.scriptCode().equals(scriptCode)) {
                    return definition.toInstrument();
                }
            }
        }
        throw new IllegalArgumentException("Unable to resolve ICICI payload: " + payload);
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public int catalogSize() {
        return definitions.size();
    }

    @Override
    public List<Instrument> allInstruments() {
        List<Instrument> instruments = new ArrayList<>(definitions.size());
        for (BreezeInstrumentDefinition definition : definitions.values()) {
            instruments.add(definition.toInstrument());
        }
        return instruments;
    }
}
