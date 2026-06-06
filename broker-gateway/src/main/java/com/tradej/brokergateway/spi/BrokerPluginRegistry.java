package com.tradej.brokergateway.spi;

import com.tradej.brokergateway.result.BrokerSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hot-reloadable registry for broker plugins.
 * Unlike ServiceLoaderBrokerRegistry (which is one-shot at startup),
 * this registry supports runtime registration and deregistration of providers.
 *
 * <p>Thread-safe. Listeners are notified on registration/deregistration events.
 */
public final class BrokerPluginRegistry {

    private final ConcurrentHashMap<BrokerSource, BrokerProvider> providers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<PluginListener> listeners = new CopyOnWriteArrayList<>();

    public void register(BrokerProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        if (!provider.isEnabled()) return;
        BrokerProvider previous = providers.put(provider.source(), provider);
        for (PluginListener listener : listeners) {
            if (previous == null) {
                listener.onPluginRegistered(provider);
            } else {
                listener.onPluginReloaded(previous, provider);
            }
        }
    }

    public void unregister(BrokerSource source) {
        BrokerProvider removed = providers.remove(source);
        if (removed != null) {
            for (PluginListener listener : listeners) {
                listener.onPluginUnregistered(removed);
            }
        }
    }

    public Optional<BrokerProvider> provider(BrokerSource source) {
        return Optional.ofNullable(providers.get(source));
    }

    public Optional<BrokerProvider> provider(String name) {
        return providers.values().stream()
                .filter(p -> p.displayName().equalsIgnoreCase(name) || p.source().name().equalsIgnoreCase(name))
                .findFirst();
    }

    public Set<BrokerSource> availableSources() {
        return Set.copyOf(providers.keySet());
    }

    public List<BrokerDescriptor> descriptors() {
        return providers.values().stream()
                .map(BrokerProvider::descriptor)
                .toList();
    }

    public int size() {
        return providers.size();
    }

    public void addListener(PluginListener listener) {
        listeners.add(listener);
    }

    public void removeListener(PluginListener listener) {
        listeners.remove(listener);
    }

    /**
     * Listener for plugin lifecycle events.
     */
    public interface PluginListener {
        default void onPluginRegistered(BrokerProvider provider) {}
        default void onPluginUnregistered(BrokerProvider provider) {}
        default void onPluginReloaded(BrokerProvider oldProvider, BrokerProvider newProvider) {}
    }
}
