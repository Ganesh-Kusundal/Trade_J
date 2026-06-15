package com.tradej.brokergateway.spi;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.broker.api.spi.BrokerRegistry;
import com.tradej.broker.api.spi.DefaultBrokerRegistry;
import com.tradej.broker.api.spi.ServiceLoaderBrokerRegistry;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.brokergateway.config.BrokerProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerRegistryTest {

    private BrokerProvider mockProvider(BrokerSource source, String displayName) {
        BrokerProvider provider = mock(BrokerProvider.class);
        when(provider.source()).thenReturn(source);
        when(provider.displayName()).thenReturn(displayName);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.descriptor()).thenReturn(new BrokerDescriptor(
                source, displayName,
                Map.of("MarketDataProvider", true, "NewsProvider", false),
                Map.of("environment", "LIVE"),
                List.of("NSE_EQ", "IDX_I"),
                "10 req/s"
        ));
        return provider;
    }

    @Test
    void registerAndLookup() {
        DefaultBrokerRegistry registry = new DefaultBrokerRegistry();
        BrokerProvider dhan = mockProvider(BrokerSource.DHAN, "DhanHQ");

        registry.register(dhan);

        Optional<BrokerProvider> found = registry.provider(BrokerSource.DHAN);
        assertTrue(found.isPresent());
        assertEquals("DhanHQ", found.get().displayName());
    }

    @Test
    void lookupByName() {
        DefaultBrokerRegistry registry = new DefaultBrokerRegistry();
        registry.register(mockProvider(BrokerSource.DHAN, "DhanHQ"));

        assertTrue(registry.provider("dhan").isPresent());
        assertTrue(registry.provider("DHAN").isPresent());
        assertFalse(registry.provider("upstox").isPresent());
        assertFalse(registry.provider("invalid").isPresent());
        assertFalse(registry.provider("").isPresent());
    }

    @Test
    void unregister() {
        DefaultBrokerRegistry registry = new DefaultBrokerRegistry();
        registry.register(mockProvider(BrokerSource.DHAN, "DhanHQ"));
        assertTrue(registry.provider(BrokerSource.DHAN).isPresent());

        registry.unregister(BrokerSource.DHAN);
        assertFalse(registry.provider(BrokerSource.DHAN).isPresent());
    }

    @Test
    void descriptors() {
        DefaultBrokerRegistry registry = new DefaultBrokerRegistry();
        registry.register(mockProvider(BrokerSource.DHAN, "DhanHQ"));
        registry.register(mockProvider(BrokerSource.UPSTOX, "Upstox"));

        List<BrokerDescriptor> descriptors = registry.descriptors();
        assertEquals(2, descriptors.size());
    }

    @Test
    void availableSources() {
        DefaultBrokerRegistry registry = new DefaultBrokerRegistry();
        registry.register(mockProvider(BrokerSource.DHAN, "DhanHQ"));
        registry.register(mockProvider(BrokerSource.ICICI, "ICICI Direct"));

        var sources = registry.availableSources();
        assertEquals(2, sources.size());
        assertTrue(sources.contains(BrokerSource.DHAN));
        assertTrue(sources.contains(BrokerSource.ICICI));
    }

    @Test
    void serviceLoaderRegistryWithExplicitProviders() {
        BrokerProvider dhan = mockProvider(BrokerSource.DHAN, "DhanHQ");
        BrokerProvider upstox = mockProvider(BrokerSource.UPSTOX, "Upstox");

        ServiceLoaderBrokerRegistry registry = new ServiceLoaderBrokerRegistry(List.of(dhan, upstox));

        assertEquals(2, registry.availableSources().size());
        assertTrue(registry.provider(BrokerSource.DHAN).isPresent());
        assertTrue(registry.provider(BrokerSource.UPSTOX).isPresent());
    }

    @Test
    void disabledProvidersAreSkipped() {
        BrokerProvider enabled = mockProvider(BrokerSource.DHAN, "DhanHQ");
        BrokerProvider disabled = mockProvider(BrokerSource.UPSTOX, "Upstox");
        when(disabled.isEnabled()).thenReturn(false);

        ServiceLoaderBrokerRegistry registry = new ServiceLoaderBrokerRegistry(List.of(enabled, disabled));

        assertEquals(1, registry.availableSources().size());
        assertTrue(registry.provider(BrokerSource.DHAN).isPresent());
        assertFalse(registry.provider(BrokerSource.UPSTOX).isPresent());
    }

    @Test
    void brokerDescriptorSupports() {
        BrokerDescriptor descriptor = new BrokerDescriptor(
                BrokerSource.DHAN, "DhanHQ",
                Map.of("MarketDataProvider", true, "NewsProvider", false, "BracketOrderProvider", true),
                Map.of(), List.of("NSE_EQ"), "10 req/s"
        );

        assertTrue(descriptor.supports("MarketDataProvider"));
        assertTrue(descriptor.supports("BracketOrderProvider"));
        assertFalse(descriptor.supports("NewsProvider"));
        assertFalse(descriptor.supports("NonExistent"));
        assertEquals(2, descriptor.supportedCount());
        assertEquals(3, descriptor.totalCount());
    }

    @Test
    void registerNullThrows() {
        DefaultBrokerRegistry registry = new DefaultBrokerRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
    }
}
