package com.tradej.strategy.spi;

import com.tradej.strategy.api.GraphStrategyPlugin;
import com.tradej.strategy.api.StrategyPluginProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registry for discovering and accessing strategy plugins via {@link ServiceLoader}.
 *
 * <p>Discovers both {@link StrategyPluginProvider} and {@link GraphStrategyPlugin}
 * implementations from the classpath.
 *
 * <p>Usage:
 * <pre>
 *   StrategyRegistry registry = StrategyRegistry.discover();
 *   registry.get("breakout").ifPresent(strategy -&gt; { ... });
 * </pre>
 */
public final class StrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(StrategyRegistry.class);

    private final Map<String, GraphStrategyPlugin> graphPlugins;
    private final Map<String, StrategyPluginProvider> providerPlugins;

    private StrategyRegistry(
            Map<String, GraphStrategyPlugin> graphPlugins,
            Map<String, StrategyPluginProvider> providerPlugins
    ) {
        this.graphPlugins = Collections.unmodifiableMap(graphPlugins);
        this.providerPlugins = Collections.unmodifiableMap(providerPlugins);
    }

    /**
     * Discover all strategy plugin implementations on the classpath via {@link ServiceLoader}.
     */
    public static StrategyRegistry discover() {
        Map<String, GraphStrategyPlugin> graphMap = new LinkedHashMap<>();
        ServiceLoader.load(GraphStrategyPlugin.class).forEach(plugin -> {
            if (graphMap.containsKey(plugin.name())) {
                log.warn("Duplicate graph strategy plugin '{}' — keeping first registration", plugin.name());
            } else {
                graphMap.put(plugin.name(), plugin);
                log.info("Registered graph strategy: {}", plugin.name());
            }
        });

        Map<String, StrategyPluginProvider> providerMap = new LinkedHashMap<>();
        ServiceLoader.load(StrategyPluginProvider.class).forEach(provider -> {
            if (provider.isEnabled()) {
                if (providerMap.containsKey(provider.name())) {
                    log.warn("Duplicate strategy provider '{}' — keeping first registration", provider.name());
                } else {
                    providerMap.put(provider.name(), provider);
                    log.info("Registered strategy provider: {} ({})", provider.name(), provider.displayName());
                }
            }
        });

        log.info("StrategyRegistry initialized with {} graph plugins, {} provider plugins",
                graphMap.size(), providerMap.size());
        return new StrategyRegistry(graphMap, providerMap);
    }

    /**
     * Look up a graph strategy plugin by name.
     */
    public Optional<GraphStrategyPlugin> graphPlugin(String name) {
        return Optional.ofNullable(graphPlugins.get(name));
    }

    /**
     * Look up a strategy provider by name.
     */
    public Optional<StrategyPluginProvider> provider(String name) {
        return Optional.ofNullable(providerPlugins.get(name));
    }

    /**
     * Returns all registered graph plugin names.
     */
    public List<String> graphPluginNames() {
        return List.copyOf(graphPlugins.keySet());
    }

    /**
     * Returns all registered provider names.
     */
    public List<String> providerNames() {
        return List.copyOf(providerPlugins.keySet());
    }

    /**
     * Returns all registered strategy names (graph + provider combined).
     */
    public List<String> allNames() {
        Map<String, Object> combined = new LinkedHashMap<>();
        combined.putAll(graphPlugins);
        combined.putAll(providerPlugins);
        return List.copyOf(combined.keySet());
    }

    /**
     * Returns all graph plugins.
     */
    public Map<String, GraphStrategyPlugin> graphPlugins() {
        return graphPlugins;
    }

    /**
     * Returns all provider plugins.
     */
    public Map<String, StrategyPluginProvider> providerPlugins() {
        return providerPlugins;
    }

    /**
     * Total number of registered strategies.
     */
    public int size() {
        return graphPlugins.size() + providerPlugins.size();
    }
}
