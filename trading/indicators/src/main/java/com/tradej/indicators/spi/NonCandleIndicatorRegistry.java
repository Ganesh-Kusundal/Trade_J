package com.tradej.indicators.spi;

import com.tradej.core.domain.model.Trade;
import com.tradej.indicators.TickLevelCVD;
import com.tradej.indicators.VolumeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registry for discovering and accessing {@link NonCandleIndicatorProvider}
 * implementations via {@link ServiceLoader}.
 *
 * <p>This registry is the counterpart of {@link IndicatorRegistry}, but for
 * indicators whose input shape is not a per-candle series (e.g. trade-book
 * or tick-stream indicators). The two registries must not be merged: the
 * per-candle {@code IndicatorProvider} contract cannot honestly represent
 * the inputs and outputs of a non-candle indicator.
 *
 * <p>Usage:
 * <pre>
 *   NonCandleIndicatorRegistry registry = NonCandleIndicatorRegistry.discover();
 *   registry.get("volume-profile").ifPresent(p -> {
 *       VolumeProfile vp = p.computeFromTrades(trades);
 *       long poc = vp.pointOfControl();
 *   });
 * </pre>
 */
public final class NonCandleIndicatorRegistry {

    private static final Logger log = LoggerFactory.getLogger(NonCandleIndicatorRegistry.class);

    private final Map<String, NonCandleIndicatorProvider> providers;

    private NonCandleIndicatorRegistry(Map<String, NonCandleIndicatorProvider> providers) {
        this.providers = Collections.unmodifiableMap(providers);
    }

    /**
     * Discover all {@link NonCandleIndicatorProvider} implementations on the
     * classpath via {@link ServiceLoader}.
     */
    public static NonCandleIndicatorRegistry discover() {
        Map<String, NonCandleIndicatorProvider> map = new LinkedHashMap<>();
        ServiceLoader.load(NonCandleIndicatorProvider.class).forEach(provider -> {
            if (map.containsKey(provider.name())) {
                log.warn("Duplicate non-candle indicator provider '{}' — keeping first registration",
                        provider.name());
            } else {
                map.put(provider.name(), provider);
                log.info("Registered non-candle indicator: {} ({})",
                        provider.name(), provider.displayName());
            }
        });
        log.info("NonCandleIndicatorRegistry initialized with {} providers: {}",
                map.size(), map.keySet());
        return new NonCandleIndicatorRegistry(map);
    }

    /**
     * Create a registry from an explicit set of providers (useful for testing).
     */
    public static NonCandleIndicatorRegistry of(NonCandleIndicatorProvider... providers) {
        Map<String, NonCandleIndicatorProvider> map = new LinkedHashMap<>();
        for (NonCandleIndicatorProvider p : providers) {
            map.put(p.name(), p);
        }
        return new NonCandleIndicatorRegistry(map);
    }

    /**
     * Look up a non-candle indicator provider by name.
     */
    public Optional<NonCandleIndicatorProvider> get(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    /**
     * Returns true if a provider is registered for the given name.
     */
    public boolean has(String name) {
        return providers.containsKey(name);
    }

    /**
     * Compute a VolumeProfile from a list of trades, by name.
     *
     * @return the populated {@link VolumeProfile}, or {@code null} if no
     *         provider is registered for {@code name}
     * @throws UnsupportedOperationException if the resolved provider does
     *         not consume trade events
     */
    public VolumeProfile computeFromTrades(String name, List<Trade> trades) {
        NonCandleIndicatorProvider provider = providers.get(name);
        if (provider == null) {
            log.warn("Non-candle indicator '{}' not registered", name);
            return null;
        }
        return provider.computeFromTrades(trades);
    }

    /**
     * Compute a CVD snapshot from a list of ticks, by name.
     *
     * @return the populated {@link TickLevelCVD.CvdSnapshot}, or {@code null}
     *         if no provider is registered for {@code name}
     * @throws UnsupportedOperationException if the resolved provider does
     *         not consume tick data
     */
    public TickLevelCVD.CvdSnapshot computeFromTicks(String name, List<long[]> ticks) {
        NonCandleIndicatorProvider provider = providers.get(name);
        if (provider == null) {
            log.warn("Non-candle indicator '{}' not registered", name);
            return null;
        }
        return provider.computeFromTicks(ticks);
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
    public Map<String, NonCandleIndicatorProvider> all() {
        return providers;
    }

    /**
     * Returns the number of registered providers.
     */
    public int size() {
        return providers.size();
    }
}
