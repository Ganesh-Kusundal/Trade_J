package com.tradej.pipeline.spi.builtin;

import com.tradej.core.domain.event.ScanHitProduced;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.PipelineNodeProvider;
import com.tradej.scanner.node.ScanAggregatorNode;

import java.util.List;
import java.util.Map;

/**
 * Built-in provider for the SCAN_AGGREGATOR node type.
 */
public final class ScanAggregatorNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.SCAN_AGGREGATOR;
    }

    @Override
    public String displayName() {
        return "Scan Aggregator";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "scanner",
                "Accumulates, deduplicates, and ranks scan hits by time window",
                List.of(new NodeTypeDescriptor.EventType(ScanHitProduced.class, "Criterion hits")),
                List.of(new NodeTypeDescriptor.EventType(ScanResultsPublished.class, "Ranked results")),
                Map.of(
                        "windowMs", NodeTypeDescriptor.ConfigField.of("windowMs",
                                NodeTypeDescriptor.ConfigField.FieldType.NUMBER,
                                "Window (ms)", 60000L),
                        "maxHits", NodeTypeDescriptor.ConfigField.of("maxHits",
                                NodeTypeDescriptor.ConfigField.FieldType.NUMBER,
                                "Max Hits", 20)
                )));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "scanner",
                "Accumulates, deduplicates, and ranks scan hits by time window",
                List.of(new NodeTypeDescriptor.EventType(ScanHitProduced.class, "Criterion hits")),
                List.of(new NodeTypeDescriptor.EventType(ScanResultsPublished.class, "Ranked results")),
                Map.of(
                        "windowMs", NodeTypeDescriptor.ConfigField.of("windowMs",
                                NodeTypeDescriptor.ConfigField.FieldType.NUMBER,
                                "Window (ms)", 60000L),
                        "maxHits", NodeTypeDescriptor.ConfigField.of("maxHits",
                                NodeTypeDescriptor.ConfigField.FieldType.NUMBER,
                                "Max Hits", 20)
                ),
                ScanAggregatorNodeProvider::buildNode));
    }

    private static PipelineNode buildNode(PipelineNodeDef def) {
        return new ScanAggregatorNode(
                stringConfig(def, "profileId", "default"),
                longConfig(def, "windowMs", 60000L),
                (int) longConfig(def, "maxHits", 20));
    }

    private static String stringConfig(PipelineNodeDef def, String key, String defaultValue) {
        if (def.config() == null) return defaultValue;
        Object v = def.config().get(key);
        return v instanceof String s && !s.isBlank() ? s : defaultValue;
    }

    private static long longConfig(PipelineNodeDef def, String key, long defaultValue) {
        if (def.config() == null) return defaultValue;
        Object v = def.config().get(key);
        return v instanceof Number n ? n.longValue() : defaultValue;
    }
}
