package com.tradej.broker.api.spi;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Default in-memory implementation of {@link BrokerRegistry}.
 */
public final class DefaultBrokerRegistry implements BrokerRegistry {

    private final Map<BrokerSource, BrokerProvider> providers = new LinkedHashMap<>();

    @Override
    public synchronized void register(BrokerProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider must not be null");
        }
        providers.put(provider.source(), provider);
    }

    @Override
    public synchronized void unregister(BrokerSource source) {
        providers.remove(source);
    }

    @Override
    public synchronized Optional<BrokerProvider> provider(BrokerSource source) {
        return Optional.ofNullable(providers.get(source));
    }

    @Override
    public synchronized Optional<BrokerProvider> provider(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            BrokerSource source = BrokerSource.parse(name);
            return provider(source);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized List<BrokerDescriptor> descriptors() {
        return providers.values().stream()
                .map(BrokerProvider::descriptor)
                .toList();
    }

    @Override
    public synchronized Set<BrokerSource> availableSources() {
        return new LinkedHashSet<>(providers.keySet());
    }
}
