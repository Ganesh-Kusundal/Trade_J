package com.tradej.brokergateway.spi;

import com.tradej.brokergateway.result.BrokerSource;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Discovers {@link BrokerProvider} implementations via {@link ServiceLoader}.
 *
 * <p>Brokers register themselves by creating a file at:
 * {@code META-INF/services/com.tradej.brokergateway.spi.BrokerProvider}
 * containing the fully qualified class name of their provider implementation.
 */
public final class ServiceLoaderBrokerRegistry implements BrokerRegistry {

    private final DefaultBrokerRegistry delegate;

    /**
     * Discover all enabled BrokerProvider implementations via ServiceLoader.
     */
    public ServiceLoaderBrokerRegistry() {
        this.delegate = new DefaultBrokerRegistry();
        ServiceLoader.load(BrokerProvider.class).forEach(provider -> {
            if (provider.isEnabled()) {
                delegate.register(provider);
            }
        });
    }

    /**
     * Create from an explicit collection of providers (useful for testing).
     */
    public ServiceLoaderBrokerRegistry(Iterable<BrokerProvider> providers) {
        this.delegate = new DefaultBrokerRegistry();
        for (BrokerProvider provider : providers) {
            if (provider.isEnabled()) {
                delegate.register(provider);
            }
        }
    }

    @Override
    public void register(BrokerProvider provider) {
        delegate.register(provider);
    }

    @Override
    public void unregister(BrokerSource source) {
        delegate.unregister(source);
    }

    @Override
    public Optional<BrokerProvider> provider(BrokerSource source) {
        return delegate.provider(source);
    }

    @Override
    public Optional<BrokerProvider> provider(String name) {
        return delegate.provider(name);
    }

    @Override
    public List<BrokerDescriptor> descriptors() {
        return delegate.descriptors();
    }

    @Override
    public Set<BrokerSource> availableSources() {
        return delegate.availableSources();
    }
}
