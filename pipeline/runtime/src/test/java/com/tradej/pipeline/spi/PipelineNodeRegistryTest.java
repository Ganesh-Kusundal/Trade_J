package com.tradej.pipeline.spi;

import com.tradej.pipeline.spi.builtin.CandleNodeProvider;
import com.tradej.pipeline.spi.builtin.FeatureNodeProvider;
import com.tradej.pipeline.spi.builtin.IngressNodeProvider;
import com.tradej.pipeline.spi.builtin.OmsNodeProvider;
import com.tradej.pipeline.spi.builtin.PortfolioNodeProvider;
import com.tradej.pipeline.spi.builtin.ReactorNodeProvider;
import com.tradej.pipeline.spi.builtin.RiskNodeProvider;
import com.tradej.pipeline.spi.builtin.ScanAggregatorNodeProvider;
import com.tradej.pipeline.spi.builtin.ScanCriterionNodeProvider;
import com.tradej.pipeline.spi.builtin.ScanNodeProvider;
import com.tradej.pipeline.spi.builtin.StrategyNodeProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PipelineNodeRegistryTest {

    @Test
    void discoverAll() {
        int providerCount = 0;
        for (PipelineNodeProvider p : ServiceLoader.load(PipelineNodeProvider.class)) {
            providerCount++;
            assertTrue(p.typeId() != null && !p.typeId().isEmpty(),
                    "Provider " + p.getClass().getName() + " has blank typeId()");
        }
        assertEquals(11, providerCount,
                "Expected 11 PipelineNodeProvider implementations on classpath");
    }

    @Test
    void getByTypeId() {
        PipelineNodeRegistry registry = new PipelineNodeRegistry();

        assertInstanceOf(IngressNodeProvider.class, registry.get("Ingress"));
        assertInstanceOf(RiskNodeProvider.class, registry.get("Risk"));
        assertInstanceOf(CandleNodeProvider.class, registry.get("Candle"));
        assertInstanceOf(FeatureNodeProvider.class, registry.get("Feature"));
        assertInstanceOf(StrategyNodeProvider.class, registry.get("Strategy"));
        assertInstanceOf(PortfolioNodeProvider.class, registry.get("Portfolio"));
        assertInstanceOf(OmsNodeProvider.class, registry.get("OMS"));
        assertInstanceOf(ReactorNodeProvider.class, registry.get("Reactor"));
        assertInstanceOf(ScanNodeProvider.class, registry.get("Scan"));
        assertInstanceOf(ScanCriterionNodeProvider.class, registry.get("ScanCriterion"));
        assertInstanceOf(ScanAggregatorNodeProvider.class, registry.get("ScanAggregator"));
    }

    @Test
    void unknownTypeIdThrows() {
        PipelineNodeRegistry registry = new PipelineNodeRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.get("nope"));
    }

    @Test
    void hasReturnsFalseForUnknownTypeId() {
        PipelineNodeRegistry registry = new PipelineNodeRegistry();
        assertFalse(registry.has("nope"));
        assertTrue(registry.has("Ingress"));
    }

    @Test
    void allReturnsEveryProvider() {
        PipelineNodeRegistry registry = new PipelineNodeRegistry();
        List<PipelineNodeProvider> all = List.copyOf(registry.all());
        assertEquals(11, all.size());

        Map<String, PipelineNodeProvider> byTypeId = new java.util.HashMap<>();
        for (PipelineNodeProvider p : all) {
            byTypeId.put(p.typeId(), p);
        }
        assertEquals(11, byTypeId.size());
        assertTrue(byTypeId.containsKey("Ingress"));
        assertTrue(byTypeId.containsKey("Scan"));
        assertTrue(byTypeId.containsKey("ScanAggregator"));
    }
}
