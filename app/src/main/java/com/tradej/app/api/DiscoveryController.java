package com.tradej.app.api;

import com.tradej.broker.api.spi.BrokerRegistry;
import com.tradej.core.domain.event.EventRegistry;
import com.tradej.core.domain.port.FeatureRegistry;
import com.tradej.indicators.spi.IndicatorRegistry;
import com.tradej.indicators.spi.TransformationRegistry;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.scanner.spi.ScannerRegistry;
import com.tradej.strategy.spi.StrategyRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified discovery endpoint — returns all platform registries in a single call.
 */
@RestController
@RequestMapping("/api/v1/discovery")
public class DiscoveryController {

    private final BrokerRegistry brokerRegistry;
    private final StrategyRegistry strategyRegistry;
    private final IndicatorRegistry indicatorRegistry;
    private final TransformationRegistry transformationRegistry;
    private final ScannerRegistry scannerRegistry;
    private final EventRegistry eventRegistry;
    private final FeatureRegistry featureRegistry;
    private final NodeRegistry nodeRegistry;

    public DiscoveryController(
            BrokerRegistry brokerRegistry,
            StrategyRegistry strategyRegistry,
            IndicatorRegistry indicatorRegistry,
            TransformationRegistry transformationRegistry,
            ScannerRegistry scannerRegistry,
            EventRegistry eventRegistry,
            FeatureRegistry featureRegistry,
            NodeRegistry nodeRegistry
    ) {
        this.brokerRegistry = brokerRegistry;
        this.strategyRegistry = strategyRegistry;
        this.indicatorRegistry = indicatorRegistry;
        this.transformationRegistry = transformationRegistry;
        this.scannerRegistry = scannerRegistry;
        this.eventRegistry = eventRegistry;
        this.featureRegistry = featureRegistry;
        this.nodeRegistry = nodeRegistry;
    }

    @GetMapping
    public Map<String, Object> discover() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("brokers", brokerRegistry.descriptors().stream()
                .map(d -> Map.of("source", d.source().name(), "displayName", d.displayName(), "segments", d.supportedSegments()))
                .toList());

        result.put("strategies", strategyRegistry.allNames().stream()
                .map(name -> Map.of("name", (Object) name))
                .toList());

        result.put("indicators", indicatorRegistry.names().stream()
                .map(name -> Map.of("name", (Object) name))
                .toList());

        result.put("transformations", transformationRegistry.names().stream()
                .map(name -> Map.of("name", (Object) name))
                .toList());

        result.put("scanners", scannerRegistry.names().stream()
                .map(name -> Map.of("name", (Object) name))
                .toList());

        result.put("events", eventRegistry.all().stream()
                .map(e -> Map.of("name", e.name(), "category", e.category(), "schemaVersion", e.schemaVersion()))
                .toList());

        result.put("features", featureRegistry.allFeatures().stream()
                .sorted()
                .map(name -> Map.of("name", name, "enabled", featureRegistry.isEnabled(name)))
                .toList());

        result.put("nodeTypes", nodeRegistry.all().entrySet().stream()
                .map(e -> Map.of("typeId", e.getKey(), "category", e.getValue().category()))
                .toList());

        return result;
    }
}
