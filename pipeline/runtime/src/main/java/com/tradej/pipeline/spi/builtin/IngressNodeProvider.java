package com.tradej.pipeline.spi.builtin;

import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.IngressNode;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.PipelineNodeProvider;

import java.util.List;
import java.util.Map;

/**
 * Built-in provider for the INGRESS node type.
 */
public final class IngressNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.INGRESS;
    }

    @Override
    public String displayName() {
        return "Ingress";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "io",
                "Pass-through ingress node for external event injection",
                List.of(),
                List.of(),
                Map.of(
                        "eventTypes", NodeTypeDescriptor.ConfigField.of("eventTypes",
                                NodeTypeDescriptor.ConfigField.FieldType.JSON,
                                "Accepted Event Types", List.of("CandleClosed")),
                        "intervals", NodeTypeDescriptor.ConfigField.of("intervals",
                                NodeTypeDescriptor.ConfigField.FieldType.INTERVAL_LIST,
                                "Filter Intervals", List.of()),
                        "symbols", NodeTypeDescriptor.ConfigField.of("symbols",
                                NodeTypeDescriptor.ConfigField.FieldType.SYMBOL_LIST,
                                "Filter Symbols", List.of())
                )));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        // Overwrite the metadata-only descriptor with one carrying the real factory.
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "io",
                "Pass-through ingress node for external event injection",
                List.of(),
                List.of(),
                Map.of(
                        "eventTypes", NodeTypeDescriptor.ConfigField.of("eventTypes",
                                NodeTypeDescriptor.ConfigField.FieldType.JSON,
                                "Accepted Event Types", List.of("CandleClosed")),
                        "intervals", NodeTypeDescriptor.ConfigField.of("intervals",
                                NodeTypeDescriptor.ConfigField.FieldType.INTERVAL_LIST,
                                "Filter Intervals", List.of()),
                        "symbols", NodeTypeDescriptor.ConfigField.of("symbols",
                                NodeTypeDescriptor.ConfigField.FieldType.SYMBOL_LIST,
                                "Filter Symbols", List.of())
                ),
                def -> new IngressNode()));
    }
}
