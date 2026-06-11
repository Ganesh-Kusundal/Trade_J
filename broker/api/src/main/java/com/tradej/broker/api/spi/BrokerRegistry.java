package com.tradej.broker.api.spi;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Registry for broker providers. Supports lookup by source enum or name.
 */
public interface BrokerRegistry {

    /**
     * Register a broker provider.
     */
    void register(BrokerProvider provider);

    /**
     * Unregister a broker by source.
     */
    void unregister(BrokerSource source);

    /**
     * Look up a provider by source enum.
     */
    Optional<BrokerProvider> provider(BrokerSource source);

    /**
     * Look up a provider by name (case-insensitive).
     */
    Optional<BrokerProvider> provider(String name);

    /**
     * Return descriptors for all registered brokers.
     */
    List<BrokerDescriptor> descriptors();

    /**
     * Return the set of available broker sources.
     */
    Set<BrokerSource> availableSources();
}
