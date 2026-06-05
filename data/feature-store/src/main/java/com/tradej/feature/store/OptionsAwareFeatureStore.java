package com.tradej.feature.store;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.GammaExposureComputed;
import com.tradej.core.domain.event.GreeksComputed;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.model.FeatureVector;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.FeatureStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decorates a {@link FeatureStore} with options analytics context keyed by underlying symbol.
 * Strategy plugins can call {@link #optionsContext(String)} for Greeks, max pain, and gamma.
 */
public final class OptionsAwareFeatureStore implements FeatureStore {

    private final FeatureStore delegate;
    private final ConcurrentHashMap<String, Map<String, Object>> optionsContextByUnderlying = new ConcurrentHashMap<>();

    public OptionsAwareFeatureStore(FeatureStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public void feed(DomainEvent event) {
        delegate.feed(event);
        switch (event) {
            case OptionChainUpdated chain -> optionsContextByUnderlying.compute(
                    chain.chain().underlying().canonicalSymbol(),
                    (k, v) -> merge(v, Map.of(
                            "spotPricePaisa", chain.chain().spotPricePaisa(),
                            "chainStrikes", chain.chain().strikes().size()
                    )));
            case GreeksComputed greeks -> optionsContextByUnderlying.compute(
                    greeks.instrumentKey().symbol(),
                    (k, v) -> merge(v, Map.of(
                            "delta", greeks.greeks().delta(),
                            "gamma", greeks.greeks().gamma(),
                            "theta", greeks.greeks().theta(),
                            "vega", greeks.greeks().vega(),
                            "iv", greeks.greeks().impliedVolatility()
                    )));
            case MaxPainComputed maxPain -> optionsContextByUnderlying.compute(
                    maxPain.underlying(),
                    (k, v) -> merge(v, Map.of(
                            "maxPainStrikePaisa", maxPain.maxPainStrikePaisa(),
                            "totalPainPaisa", maxPain.totalPainPaisa()
                    )));
            case GammaExposureComputed gamma -> optionsContextByUnderlying.compute(
                    gamma.underlying(),
                    (k, v) -> merge(v, Map.of("netGamma", gamma.netGamma())));
            default -> { }
        }
    }

    @Override
    public Optional<FeatureVector> getFeatures(String symbol, String interval, int lookback) {
        return delegate.getFeatures(symbol, interval, lookback);
    }

    public Map<String, Object> optionsContext(String underlying) {
        return Map.copyOf(optionsContextByUnderlying.getOrDefault(underlying, Map.of()));
    }

    public FeatureStore delegate() {
        return delegate;
    }

    private static Map<String, Object> merge(Map<String, Object> existing, Map<String, Object> update) {
        Map<String, Object> map = existing == null ? new HashMap<>() : new HashMap<>(existing);
        map.putAll(update);
        return map;
    }
}
