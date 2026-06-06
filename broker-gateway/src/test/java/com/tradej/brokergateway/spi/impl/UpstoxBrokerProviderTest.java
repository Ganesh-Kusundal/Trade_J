package com.tradej.brokergateway.spi.impl;

import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerDescriptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UpstoxBrokerProviderTest {

    private final UpstoxBrokerProvider provider = new UpstoxBrokerProvider();

    @Test
    void sourceIsUpstox() {
        assertEquals(BrokerSource.UPSTOX, provider.source());
    }

    @Test
    void displayNameIsUpstox() {
        assertEquals("Upstox", provider.displayName());
    }

    @Test
    void descriptorHasCorrectCapabilities() {
        BrokerDescriptor desc = provider.descriptor();
        assertNotNull(desc);
        assertTrue(desc.capabilities().get("MarketDataProvider"));
        assertTrue(desc.capabilities().get("NewsProvider"));
        assertTrue(desc.capabilities().get("SliceOrderCommand"));
        assertFalse(desc.capabilities().get("BracketOrderProvider"));
        assertFalse(desc.capabilities().get("SessionRiskProvider"));
    }

    @Test
    void descriptorHasAuthModes() {
        BrokerDescriptor desc = provider.descriptor();
        assertTrue(desc.metadata().containsKey("authModes"));
    }
}
