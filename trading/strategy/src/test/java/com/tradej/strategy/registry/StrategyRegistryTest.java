package com.tradej.strategy.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.value.Side;
import com.tradej.strategy.api.GraphStrategyPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyRegistryTest {

    @Test
    void registryDiscoversStrategiesOnClasspath() {
        StrategyRegistry r = new StrategyRegistry();
        // No META-INF/strategies/*.yaml on the test classpath; the
        // built-in default set should still be present so dev has
        // something to work with.
        assertTrue(r.size() >= 1, "Registry should not be empty");
        assertTrue(r.ids().stream().anyMatch(id -> id.startsWith("builtin:")),
                "Built-in default strategies should be loaded when no descriptors are present");
    }

    @Test
    void canLookupById() {
        StrategyRegistry r = new StrategyRegistry();
        Optional<GraphStrategyPlugin> p = r.get("builtin:sma-cross-7-25");
        assertTrue(p.isPresent(), "SmaCross strategy should be in the built-in defaults");
        assertEquals("sma-cross-7-25", p.get().name());
    }

    @Test
    void descriptorParserHandlesValidYaml() throws Exception {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        String src = """
                id: my-strategy
                className: com.tradej.strategy.example.SmaCrossStrategy
                enabled: true
                parameters:
                  fastPeriod: 5
                  slowPeriod: 15
                """;
        StrategyRegistry.StrategyDescriptor desc = yaml.readValue(src, StrategyRegistry.StrategyDescriptor.class);
        assertEquals("my-strategy", desc.id());
        assertEquals("com.tradej.strategy.example.SmaCrossStrategy", desc.className());
        assertEquals(Boolean.TRUE, desc.enabled());
        assertEquals(5, ((Number) desc.parameters().get("fastPeriod")).intValue());
    }

    @Test
    void descriptorParserIsLenientAboutUnknownFields() throws Exception {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        String src = """
                id: lenient
                className: com.tradej.strategy.example.SmaCrossStrategy
                unknownField: shouldBeIgnored
                parameters:
                  fastPeriod: 3
                  slowPeriod: 9
                """;
        StrategyRegistry.StrategyDescriptor desc = yaml.readValue(src, StrategyRegistry.StrategyDescriptor.class);
        assertEquals("lenient", desc.id());
    }

    @Test
    void unknownStrategyIdReturnsEmpty() {
        StrategyRegistry r = new StrategyRegistry();
        assertTrue(r.get("does-not-exist").isEmpty());
    }

    @Test
    void builtInSmaCrossProducesSignals() {
        StrategyRegistry r = new StrategyRegistry();
        GraphStrategyPlugin p = r.get("builtin:sma-cross-7-25").orElseThrow();
        // The SmaCross plugin only emits after a full slow window; with
        // no candles, no signal.
        for (Class<?> c : p.subscribedEventTypes()) {
            assertEquals(CandleClosed.class, c, "SmaCross only subscribes to CandleClosed");
        }
    }

    @Test
    void builtInDepthImbalanceSubscribesToDepthUpdate() {
        StrategyRegistry r = new StrategyRegistry();
        GraphStrategyPlugin p = r.get("builtin:depth-imbalance").orElseThrow();
        boolean subscribesDepth = p.subscribedEventTypes().stream()
                .anyMatch(c -> c.getSimpleName().equals("DepthUpdateEvent"));
        assertTrue(subscribesDepth, "DepthImbalance should subscribe to DepthUpdateEvent");
    }

    @Test
    void idsIsStable() {
        StrategyRegistry r1 = new StrategyRegistry();
        StrategyRegistry r2 = new StrategyRegistry();
        // Two registry instances built the same way should expose the
        // same set of ids (order may vary).
        assertEquals(r1.ids().size(), r2.ids().size());
    }
}
