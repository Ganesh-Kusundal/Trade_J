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
 * Registry for discovering and accessing {@link IndicatorProvider} implementations
 * via {@link ServiceLoader}.
 *
 * <p>Usage:
 * <pre>
 *   IndicatorRegistry registry = IndicatorRegistry.discover();
 *   registry.get("rsi").ifPresent(rsi -> {
 *       List&lt;Double&gt; values = rsi.calculate(candles);
 *   });
 * </pre>
 */
public final class IndicatorRegistry {

    private static final Logger log = LoggerFactory.getLogger(IndicatorRegistry.class);

    private final Map<String, IndicatorProvider> providers;

    private IndicatorRegistry(Map<String, IndicatorProvider> providers) {
        this.providers = Collections.unmodifiableMap(providers);
    }

    /**
     * Discover all {@link IndicatorProvider} implementations on the classpath
     * via {@link ServiceLoader}.
     */
    public static IndicatorRegistry discover() {
        Map<String, IndicatorProvider> map = new LinkedHashMap<>();
        ServiceLoader.load(IndicatorProvider.class).forEach(provider -> {
            if (map.containsKey(provider.name())) {
                log.warn("Duplicate indicator provider '{}' — keeping first registration", provider.name());
            } else {
                map.put(provider.name(), provider);
                log.info("Registered indicator: {} ({})", provider.name(), provider.displayName());
            }
        });
        log.info("IndicatorRegistry initialized with {} providers: {}", map.size(), map.keySet());
        return new IndicatorRegistry(map);
    }

    /**
     * Create a registry from an explicit set of providers (useful for testing).
     */
    public static IndicatorRegistry of(IndicatorProvider... providers) {
        Map<String, IndicatorProvider> map = new LinkedHashMap<>();
        for (IndicatorProvider p : providers) {
            map.put(p.name(), p);
        }
        return new IndicatorRegistry(map);
    }

    /**
     * Look up an indicator by name.
     */
    public Optional<IndicatorProvider> get(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    /**
     * Look up and calculate an indicator, returning an empty list if not found.
     */
    public List<Double> calculate(String name, List<Candle> candles) {
        IndicatorProvider provider = providers.get(name);
        if (provider == null) {
            log.warn("Indicator '{}' not registered", name);
            return List.of();
        }
        return provider.calculate(candles);
    }

    /**
     * Returns all registered provider names.
     */
    public List<String> names() {
        return List.copyOf(providers.keySet());
    }

    /**
     * Returns all registered providers.
     */
    public Map<String, IndicatorProvider> all() {
        return providers;
    }

    /**
     * Returns the number of registered providers.
     */
    public int size() {
        return providers.size();
    }
}
