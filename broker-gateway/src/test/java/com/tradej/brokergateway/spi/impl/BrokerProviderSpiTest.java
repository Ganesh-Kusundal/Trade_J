package com.tradej.brokergateway.spi.impl;

import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.broker.api.spi.BrokerRegistry;
import com.tradej.broker.api.spi.ServiceLoaderBrokerRegistry;
import com.tradej.broker.api.spi.BrokerSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that all SPI providers are correctly registered and describe their capabilities.
 */
class BrokerProviderSpiTest {

    @Test
    void serviceLoaderDiscoversAllFourProviders() {
        ServiceLoaderBrokerRegistry registry = new ServiceLoaderBrokerRegistry();

        var sources = registry.availableSources();
        assertTrue(sources.contains(BrokerSource.DHAN), "Dhan provider should be discovered");
        assertTrue(sources.contains(BrokerSource.UPSTOX), "Upstox provider should be discovered");
        assertTrue(sources.contains(BrokerSource.ICICI), "ICICI provider should be discovered");
        assertTrue(sources.contains(BrokerSource.SIMULATION), "Simulation provider should be discovered");
        assertEquals(4, sources.size());
    }

    @Test
    void dhanProviderDescriptorHasCorrectCapabilities() {
        ServiceLoaderBrokerRegistry registry = new ServiceLoaderBrokerRegistry();
        BrokerProvider dhan = registry.provider(BrokerSource.DHAN).orElseThrow();

        assertEquals("DhanHQ", dhan.displayName());

        BrokerDescriptor descriptor = dhan.descriptor();
        assertTrue(descriptor.supports("MarketDataProvider"));
        assertTrue(descriptor.supports("BracketOrderProvider"));
        assertTrue(descriptor.supports("GttOrderProvider"));
        assertTrue(descriptor.supports("SliceOrderCommand"));
        assertFalse(descriptor.supports("NewsProvider"));
        assertTrue(descriptor.supportedCount() >= 14);
    }

    @Test
    void upstoxProviderDescriptorHasCorrectCapabilities() {
        ServiceLoaderBrokerRegistry registry = new ServiceLoaderBrokerRegistry();
        BrokerProvider upstox = registry.provider(BrokerSource.UPSTOX).orElseThrow();

        assertEquals("Upstox", upstox.displayName());

        BrokerDescriptor descriptor = upstox.descriptor();
        assertTrue(descriptor.supports("MarketDataProvider"));
        assertTrue(descriptor.supports("NewsProvider"));
        assertTrue(descriptor.supports("GttOrderProvider"));
        assertFalse(descriptor.supports("BracketOrderProvider"));
    }

    @Test
    void iciciProviderDescriptorHasCorrectCapabilities() {
        ServiceLoaderBrokerRegistry registry = new ServiceLoaderBrokerRegistry();
        BrokerProvider icici = registry.provider(BrokerSource.ICICI).orElseThrow();

        assertEquals("ICICI Direct", icici.displayName());

        BrokerDescriptor descriptor = icici.descriptor();
        assertTrue(descriptor.supports("MarketDataProvider"));
        assertTrue(descriptor.supports("OptionsProvider"));
        assertFalse(descriptor.supports("BracketOrderProvider"));
        assertFalse(descriptor.supports("GttOrderProvider"));
        assertFalse(descriptor.supports("NewsProvider"));
    }

    @Test
    void simulationProviderDescriptorHasCorrectCapabilities() {
        ServiceLoaderBrokerRegistry registry = new ServiceLoaderBrokerRegistry();
        BrokerProvider sim = registry.provider(BrokerSource.SIMULATION).orElseThrow();

        assertEquals("Paper Trading", sim.displayName());

        BrokerDescriptor descriptor = sim.descriptor();
        assertTrue(descriptor.supports("MarketDataProvider"));
        assertTrue(descriptor.supports("OrderCommand"));
        assertFalse(descriptor.supports("WebSocketMultiplexer"));
        assertFalse(descriptor.supports("NewsProvider"));
    }

    @Test
    void allDescriptorsHaveSupportedSegments() {
        ServiceLoaderBrokerRegistry registry = new ServiceLoaderBrokerRegistry();

        for (BrokerDescriptor descriptor : registry.descriptors()) {
            assertFalse(descriptor.supportedSegments().isEmpty(),
                    descriptor.displayName() + " should declare supported segments");
        }
    }

    @Test
    void allDescriptorsHaveRateLimitInfo() {
        ServiceLoaderBrokerRegistry registry = new ServiceLoaderBrokerRegistry();

        for (BrokerDescriptor descriptor : registry.descriptors()) {
            assertNotNull(descriptor.rateLimitInfo(),
                    descriptor.displayName() + " should declare rate limit info");
        }
    }

    @Test
    void connectWithNullProfileThrows() {
        ServiceLoaderBrokerRegistry registry = new ServiceLoaderBrokerRegistry();
        BrokerProvider dhan = registry.provider(BrokerSource.DHAN).orElseThrow();

        assertThrows(NullPointerException.class, () -> dhan.create(null));
    }
}
