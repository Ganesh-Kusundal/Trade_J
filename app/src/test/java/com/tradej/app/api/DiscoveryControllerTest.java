package com.tradej.app.api;

import com.tradej.broker.api.spi.BrokerRegistry;
import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.core.domain.event.EventCatalogEntry;
import com.tradej.core.domain.event.EventRegistry;
import com.tradej.core.domain.port.FeatureRegistry;
import com.tradej.indicators.spi.IndicatorRegistry;
import com.tradej.indicators.spi.TransformationRegistry;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.scanner.spi.ScannerRegistry;
import com.tradej.strategy.spi.StrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for the unified /api/v1/discovery endpoint.
 * Validates that all platform registries are exposed in a single response.
 */
@Tag("unit")
class DiscoveryControllerTest {

    private DiscoveryController controller;
    private BrokerRegistry brokerRegistry;
    private StrategyRegistry strategyRegistry;
    private IndicatorRegistry indicatorRegistry;
    private TransformationRegistry transformationRegistry;
    private ScannerRegistry scannerRegistry;
    private EventRegistry eventRegistry;
    private FeatureRegistry featureRegistry;
    private NodeRegistry nodeRegistry;

    @BeforeEach
    void setUp() {
        brokerRegistry = mock(BrokerRegistry.class);
        strategyRegistry = mock(StrategyRegistry.class);
        indicatorRegistry = mock(IndicatorRegistry.class);
        transformationRegistry = mock(TransformationRegistry.class);
        scannerRegistry = mock(ScannerRegistry.class);
        eventRegistry = mock(EventRegistry.class);
        featureRegistry = mock(FeatureRegistry.class);
        nodeRegistry = mock(NodeRegistry.class);

        controller = new DiscoveryController(
                brokerRegistry, strategyRegistry, indicatorRegistry,
                transformationRegistry, scannerRegistry, eventRegistry,
                featureRegistry, nodeRegistry
        );
    }

    @Test
    @DisplayName("Discovery returns all registry sections")
    void discoveryReturnsAllSections() {
        when(brokerRegistry.descriptors()).thenReturn(List.of());
        when(strategyRegistry.allNames()).thenReturn(List.of("halftrend", "ema-crossover"));
        when(indicatorRegistry.names()).thenReturn(List.of("ema", "rsi", "atr"));
        when(transformationRegistry.names()).thenReturn(List.of("heikin-ashi"));
        when(scannerRegistry.names()).thenReturn(List.of("default"));
        when(eventRegistry.all()).thenReturn(List.of(
                new EventCatalogEntry("MarketTickEvent", "MarketTickEvent", "com.tradej.core.domain.event", "market", 1)
        ));
        when(featureRegistry.allFeatures()).thenReturn(Set.of("live-trading", "scanner", "replay"));
        when(featureRegistry.isEnabled(anyString())).thenReturn(true);
        when(nodeRegistry.all()).thenReturn(Map.of());

        Map<String, Object> result = controller.discover();

        assertNotNull(result);
        assertTrue(result.containsKey("brokers"));
        assertTrue(result.containsKey("strategies"));
        assertTrue(result.containsKey("indicators"));
        assertTrue(result.containsKey("transformations"));
        assertTrue(result.containsKey("scanners"));
        assertTrue(result.containsKey("events"));
        assertTrue(result.containsKey("features"));
        assertTrue(result.containsKey("nodeTypes"));
    }

    @Test
    @DisplayName("Discovery includes strategy names")
    void discoveryIncludesStrategies() {
        when(brokerRegistry.descriptors()).thenReturn(List.of());
        when(strategyRegistry.allNames()).thenReturn(List.of("halftrend", "ml-strategy"));
        when(indicatorRegistry.names()).thenReturn(List.of());
        when(transformationRegistry.names()).thenReturn(List.of());
        when(scannerRegistry.names()).thenReturn(List.of());
        when(eventRegistry.all()).thenReturn(List.of());
        when(featureRegistry.allFeatures()).thenReturn(Set.of());
        when(featureRegistry.isEnabled(anyString())).thenReturn(false);
        when(nodeRegistry.all()).thenReturn(Map.of());

        Map<String, Object> result = controller.discover();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> strategies = (List<Map<String, Object>>) result.get("strategies");
        assertEquals(2, strategies.size());
    }

    @Test
    @DisplayName("Discovery includes event catalog with categories")
    void discoveryIncludesEventCatalog() {
        when(brokerRegistry.descriptors()).thenReturn(List.of());
        when(strategyRegistry.allNames()).thenReturn(List.of());
        when(indicatorRegistry.names()).thenReturn(List.of());
        when(transformationRegistry.names()).thenReturn(List.of());
        when(scannerRegistry.names()).thenReturn(List.of());
        when(eventRegistry.all()).thenReturn(List.of(
                new EventCatalogEntry("MarketTickEvent", "MarketTickEvent", "com.tradej.core.domain.event", "market", 1),
                new EventCatalogEntry("OrderAccepted", "OrderAccepted", "com.tradej.core.domain.event", "order", 1)
        ));
        when(featureRegistry.allFeatures()).thenReturn(Set.of());
        when(featureRegistry.isEnabled(anyString())).thenReturn(false);
        when(nodeRegistry.all()).thenReturn(Map.of());

        Map<String, Object> result = controller.discover();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) result.get("events");
        assertEquals(2, events.size());
        assertEquals("market", events.get(0).get("category"));
        assertEquals("order", events.get(1).get("category"));
    }
}
