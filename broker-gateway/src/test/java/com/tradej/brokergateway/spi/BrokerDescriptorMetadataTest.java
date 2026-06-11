package com.tradej.brokergateway.spi;

import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.api.spi.CapabilityMetadata;
import com.tradej.broker.dhan.DhanBrokerProvider;
import com.tradej.broker.icici.IciciBrokerProvider;
import com.tradej.broker.upstox.UpstoxBrokerProvider;
import com.tradej.brokergateway.simulation.SimulationBrokerProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class BrokerDescriptorMetadataTest {

    private static final Set<String> VALID_CATEGORIES =
            Set.of("market", "orders", "portfolio", "streaming", "services", "risk", "other");

    static Stream<Arguments> allProviders() {
        return Stream.of(
                Arguments.of(new DhanBrokerProvider()),
                Arguments.of(new UpstoxBrokerProvider()),
                Arguments.of(new IciciBrokerProvider()),
                Arguments.of(new SimulationBrokerProvider())
        );
    }

    // ── Non-empty capabilityMetadata ────────────────────────────────

    @ParameterizedTest
    @MethodSource("allProviders")
    void capabilityMetadataIsNotEmpty(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        assertNotNull(desc.capabilityMetadata());
        assertFalse(desc.capabilityMetadata().isEmpty(),
                provider.source() + " should have non-empty capabilityMetadata");
    }

    // ── All categories are valid ────────────────────────────────────

    @ParameterizedTest
    @MethodSource("allProviders")
    void categoriesAreValid(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        for (var entry : desc.capabilityMetadata().entrySet()) {
            String category = entry.getValue().category();
            assertTrue(VALID_CATEGORIES.contains(category),
                    provider.source() + " capability '" + entry.getKey()
                            + "' has invalid category: " + category);
        }
    }

    // ── Descriptions are non-empty ──────────────────────────────────

    @ParameterizedTest
    @MethodSource("allProviders")
    void descriptionsAreNonEmpty(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        for (var entry : desc.capabilityMetadata().entrySet()) {
            String description = entry.getValue().description();
            assertNotNull(description,
                    provider.source() + " capability '" + entry.getKey() + "' has null description");
            assertFalse(description.isBlank(),
                    provider.source() + " capability '" + entry.getKey() + "' has blank description");
        }
    }

    // ── metadataFor returns correct values ──────────────────────────

    @ParameterizedTest
    @MethodSource("allProviders")
    void metadataForReturnsRegisteredMetadata(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        // Every key in capabilities should also be in capabilityMetadata
        for (String capabilityKey : desc.capabilities().keySet()) {
            CapabilityMetadata meta = desc.metadataFor(capabilityKey);
            assertNotNull(meta, provider.source() + " metadataFor('" + capabilityKey + "') returned null");
            assertFalse(meta.description().isBlank(),
                    provider.source() + " metadataFor('" + capabilityKey + "') has blank description");
        }
    }

    // ── metadataFor returns default for unknown capability ──────────

    @ParameterizedTest
    @MethodSource("allProviders")
    void metadataForReturnsDefaultForUnknownCapability(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        CapabilityMetadata meta = desc.metadataFor("NonExistentCapability");
        assertNotNull(meta);
        assertEquals("", meta.description());
        assertEquals("other", meta.category());
        assertEquals("1.0", meta.version());
    }

    // ── version() returns "1.0.0" ───────────────────────────────────

    @ParameterizedTest
    @MethodSource("allProviders")
    void versionReturns1_0_0(BrokerProvider provider) {
        assertEquals("1.0.0", provider.version(),
                provider.source() + " should return version 1.0.0");
    }

    // ── CapabilityMetadata keys match capabilities keys ─────────────

    @ParameterizedTest
    @MethodSource("allProviders")
    void capabilityMetadataKeysMatchCapabilities(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        assertEquals(desc.capabilities().keySet(), desc.capabilityMetadata().keySet(),
                provider.source() + " capabilityMetadata keys should match capabilities keys");
    }

    // ── CapabilityMetadata version is "1.0" ─────────────────────────

    @ParameterizedTest
    @MethodSource("allProviders")
    void capabilityMetadataVersionIs1_0(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        for (var entry : desc.capabilityMetadata().entrySet()) {
            assertEquals("1.0", entry.getValue().version(),
                    provider.source() + " capability '" + entry.getKey() + "' should have version 1.0");
        }
    }

    // ── Specific category spot-checks ───────────────────────────────

    @ParameterizedTest
    @MethodSource("allProviders")
    void marketDataProviderHasMarketCategory(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        CapabilityMetadata meta = desc.metadataFor("MarketDataProvider");
        assertEquals("market", meta.category(),
                provider.source() + " MarketDataProvider should be in 'market' category");
    }

    @ParameterizedTest
    @MethodSource("allProviders")
    void orderCommandHasOrdersCategory(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        CapabilityMetadata meta = desc.metadataFor("OrderCommand");
        assertEquals("orders", meta.category(),
                provider.source() + " OrderCommand should be in 'orders' category");
    }

    @ParameterizedTest
    @MethodSource("allProviders")
    void webSocketHasStreamingCategory(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        CapabilityMetadata meta = desc.metadataFor("WebSocketMultiplexer");
        assertEquals("streaming", meta.category(),
                provider.source() + " WebSocketMultiplexer should be in 'streaming' category");
    }

    @ParameterizedTest
    @MethodSource("allProviders")
    void portfolioProviderHasPortfolioCategory(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        CapabilityMetadata meta = desc.metadataFor("PortfolioProvider");
        assertEquals("portfolio", meta.category(),
                provider.source() + " PortfolioProvider should be in 'portfolio' category");
    }

    @ParameterizedTest
    @MethodSource("allProviders")
    void marginProviderHasRiskCategory(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        CapabilityMetadata meta = desc.metadataFor("MarginProvider");
        assertEquals("risk", meta.category(),
                provider.source() + " MarginProvider should be in 'risk' category");
    }

    @ParameterizedTest
    @MethodSource("allProviders")
    void instrumentResolverHasServicesCategory(BrokerProvider provider) {
        BrokerDescriptor desc = provider.descriptor();
        CapabilityMetadata meta = desc.metadataFor("InstrumentResolver");
        assertEquals("services", meta.category(),
                provider.source() + " InstrumentResolver should be in 'services' category");
    }
}
