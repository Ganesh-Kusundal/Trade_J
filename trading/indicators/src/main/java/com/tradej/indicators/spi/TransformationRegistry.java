package com.tradej.indicators.spi;

import com.tradej.core.domain.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registry for discovering and accessing {@link TransformationProvider} implementations.
 */
public final class TransformationRegistry {

    private static final Logger log = LoggerFactory.getLogger(TransformationRegistry.class);

    private final Map<String, TransformationProvider> providers;

    private TransformationRegistry(Map<String, TransformationProvider> providers) {
        this.providers = Collections.unmodifiableMap(providers);
    }

    public static TransformationRegistry discover() {
        Map<String, TransformationProvider> map = new LinkedHashMap<>();
        ServiceLoader.load(TransformationProvider.class).forEach(provider -> {
            if (map.containsKey(provider.name())) {
                log.warn("Duplicate transformation provider '{}' — keeping first", provider.name());
            } else {
                map.put(provider.name(), provider);
                log.info("Registered transformation: {} ({})", provider.name(), provider.displayName());
            }
        });
        log.info("TransformationRegistry initialized with {} providers: {}", map.size(), map.keySet());
        return new TransformationRegistry(map);
    }

    public static TransformationRegistry of(TransformationProvider... providers) {
        Map<String, TransformationProvider> map = new LinkedHashMap<>();
        for (TransformationProvider p : providers) {
            map.put(p.name(), p);
        }
        return new TransformationRegistry(map);
    }

    public Optional<TransformationProvider> get(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    public List<Candle> transform(String name, List<Candle> input, Map<String, Object> params) {
        TransformationProvider provider = providers.get(name);
        if (provider == null) {
            log.warn("Transformation '{}' not registered", name);
            return List.of();
        }
        return provider.transform(input, params);
    }

    public List<String> names() {
        return List.copyOf(providers.keySet());
    }

    public int size() {
        return providers.size();
    }
}
