package com.tradej.brokergateway.spi;

import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerPluginRegistry;
import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.broker.api.spi.BrokerSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class BrokerPluginRegistryTest {

    private BrokerPluginRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BrokerPluginRegistry();
    }

    private BrokerProvider mockProvider(BrokerSource source, String displayName) {
        BrokerProvider provider = mock(BrokerProvider.class);
        when(provider.source()).thenReturn(source);
        when(provider.displayName()).thenReturn(displayName);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.descriptor()).thenReturn(new BrokerDescriptor(
                source, displayName,
                Map.of("MarketDataProvider", true, "NewsProvider", false),
                Map.of("environment", "LIVE"),
                List.of("NSE_EQ"),
                "10 req/s"
        ));
        return provider;
    }

    @Test
    void registerAddsProvider() {
        BrokerProvider dhan = mockProvider(BrokerSource.DHAN, "DhanHQ");

        registry.register(dhan);

        Optional<BrokerProvider> found = registry.provider(BrokerSource.DHAN);
        assertTrue(found.isPresent());
        assertSame(dhan, found.get());
    }

    @Test
    void unregisterRemovesProvider() {
        BrokerProvider dhan = mockProvider(BrokerSource.DHAN, "DhanHQ");
        registry.register(dhan);
        assertTrue(registry.provider(BrokerSource.DHAN).isPresent());

        registry.unregister(BrokerSource.DHAN);

        assertFalse(registry.provider(BrokerSource.DHAN).isPresent());
    }

    @Test
    void providerByBrokerSourceReturnsCorrectProvider() {
        BrokerProvider dhan = mockProvider(BrokerSource.DHAN, "DhanHQ");
        BrokerProvider upstox = mockProvider(BrokerSource.UPSTOX, "Upstox");
        registry.register(dhan);
        registry.register(upstox);

        Optional<BrokerProvider> foundDhan = registry.provider(BrokerSource.DHAN);
        Optional<BrokerProvider> foundUpstox = registry.provider(BrokerSource.UPSTOX);

        assertTrue(foundDhan.isPresent());
        assertSame(dhan, foundDhan.get());
        assertTrue(foundUpstox.isPresent());
        assertSame(upstox, foundUpstox.get());
    }

    @Test
    void providerByStringMatchesNameCaseInsensitive() {
        BrokerProvider dhan = mockProvider(BrokerSource.DHAN, "DhanHQ");
        registry.register(dhan);

        // Match by BrokerSource enum name (case-insensitive)
        assertTrue(registry.provider("dhan").isPresent());
        assertTrue(registry.provider("DHAN").isPresent());
        assertTrue(registry.provider("Dhan").isPresent());

        // Match by displayName (case-insensitive)
        assertTrue(registry.provider("dhanhq").isPresent());
        assertTrue(registry.provider("DHANHQ").isPresent());
        assertTrue(registry.provider("DhanHQ").isPresent());

        // Non-matching
        assertFalse(registry.provider("upstox").isPresent());
        assertFalse(registry.provider("nonexistent").isPresent());
    }

    @Test
    void availableSourcesReturnsAllRegisteredSources() {
        registry.register(mockProvider(BrokerSource.DHAN, "DhanHQ"));
        registry.register(mockProvider(BrokerSource.UPSTOX, "Upstox"));
        registry.register(mockProvider(BrokerSource.ICICI, "ICICI Direct"));

        Set<BrokerSource> sources = registry.availableSources();

        assertEquals(3, sources.size());
        assertTrue(sources.contains(BrokerSource.DHAN));
        assertTrue(sources.contains(BrokerSource.UPSTOX));
        assertTrue(sources.contains(BrokerSource.ICICI));
    }

    @Test
    void descriptorsReturnsDescriptorsForAllProviders() {
        BrokerProvider dhan = mockProvider(BrokerSource.DHAN, "DhanHQ");
        BrokerProvider upstox = mockProvider(BrokerSource.UPSTOX, "Upstox");
        registry.register(dhan);
        registry.register(upstox);

        List<BrokerDescriptor> descriptors = registry.descriptors();

        assertEquals(2, descriptors.size());
        // Verify the descriptors match what the providers return
        assertTrue(descriptors.stream().anyMatch(d -> d.source() == BrokerSource.DHAN));
        assertTrue(descriptors.stream().anyMatch(d -> d.source() == BrokerSource.UPSTOX));
    }

    @Test
    void sizeReturnsCorrectCount() {
        assertEquals(0, registry.size());

        registry.register(mockProvider(BrokerSource.DHAN, "DhanHQ"));
        assertEquals(1, registry.size());

        registry.register(mockProvider(BrokerSource.UPSTOX, "Upstox"));
        assertEquals(2, registry.size());

        registry.unregister(BrokerSource.DHAN);
        assertEquals(1, registry.size());
    }

    @Test
    void addListenerReceivesOnPluginRegisteredEvent() {
        BrokerPluginRegistry.PluginListener listener = mock(BrokerPluginRegistry.PluginListener.class);
        registry.addListener(listener);
        BrokerProvider dhan = mockProvider(BrokerSource.DHAN, "DhanHQ");

        registry.register(dhan);

        verify(listener).onPluginRegistered(dhan);
        verify(listener, never()).onPluginUnregistered(any());
        verify(listener, never()).onPluginReloaded(any(), any());
    }

    @Test
    void addListenerReceivesOnPluginUnregisteredEvent() {
        BrokerPluginRegistry.PluginListener listener = mock(BrokerPluginRegistry.PluginListener.class);
        BrokerProvider dhan = mockProvider(BrokerSource.DHAN, "DhanHQ");
        registry.register(dhan);
        registry.addListener(listener);

        registry.unregister(BrokerSource.DHAN);

        verify(listener).onPluginUnregistered(dhan);
        verify(listener, never()).onPluginRegistered(any());
        verify(listener, never()).onPluginReloaded(any(), any());
    }

    @Test
    void addListenerReceivesOnPluginReloadedEventOnReRegister() {
        BrokerPluginRegistry.PluginListener listener = mock(BrokerPluginRegistry.PluginListener.class);
        BrokerProvider dhanV1 = mockProvider(BrokerSource.DHAN, "DhanHQ");
        registry.register(dhanV1);
        registry.addListener(listener);

        BrokerProvider dhanV2 = mockProvider(BrokerSource.DHAN, "DhanHQ");
        registry.register(dhanV2);

        verify(listener).onPluginReloaded(dhanV1, dhanV2);
        verify(listener, never()).onPluginRegistered(any());
        verify(listener, never()).onPluginUnregistered(any());
    }

    @Test
    void disabledProviderIsNotRegistered() {
        BrokerProvider disabled = mockProvider(BrokerSource.DHAN, "DhanHQ");
        when(disabled.isEnabled()).thenReturn(false);

        registry.register(disabled);

        assertEquals(0, registry.size());
        assertFalse(registry.provider(BrokerSource.DHAN).isPresent());
    }

    @Test
    void removeListenerStopsNotifications() {
        BrokerPluginRegistry.PluginListener listener = mock(BrokerPluginRegistry.PluginListener.class);
        registry.addListener(listener);
        registry.removeListener(listener);

        registry.register(mockProvider(BrokerSource.DHAN, "DhanHQ"));

        verify(listener, never()).onPluginRegistered(any());
        verify(listener, never()).onPluginUnregistered(any());
        verify(listener, never()).onPluginReloaded(any(), any());
    }
}
