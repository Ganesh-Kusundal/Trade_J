package com.tradej.brokergateway.spi.impl;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerDescriptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanBrokerProviderTest {

    private final DhanBrokerProvider provider = new DhanBrokerProvider();

    @Test
    void sourceIsDhan() {
        assertEquals(BrokerSource.DHAN, provider.source());
    }

    @Test
    void displayNameIsDhanHQ() {
        assertEquals("DhanHQ", provider.displayName());
    }

    @Test
    void descriptorHasCorrectCapabilities() {
        BrokerDescriptor desc = provider.descriptor();
        assertNotNull(desc);
        assertEquals(BrokerSource.DHAN, desc.source());
        assertTrue(desc.capabilities().get("MarketDataProvider"));
        assertTrue(desc.capabilities().get("OptionsProvider"));
        assertTrue(desc.capabilities().get("OrderCommand"));
        assertTrue(desc.capabilities().get("SessionRiskProvider"));
        assertFalse(desc.capabilities().get("NewsProvider"));
    }

    @Test
    void descriptorHasSegments() {
        BrokerDescriptor desc = provider.descriptor();
        assertTrue(desc.supportedSegments().contains("NSE_EQ"));
        assertTrue(desc.supportedSegments().contains("NSE_FNO"));
    }

    @Test
    void descriptorHasRateLimits() {
        BrokerDescriptor desc = provider.descriptor();
        assertNotNull(desc.rateLimitInfo());
        assertFalse(desc.rateLimitInfo().isBlank());
    }
}
