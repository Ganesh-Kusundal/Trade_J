package com.tradej.pipeline.spi.builtin;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.NoopPipelineNode;
import com.tradej.pipeline.spi.PipelineNodeProvider;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.scanner.node.ScanNode;

import java.util.List;
import java.util.Map;

/**
 * Built-in provider for the SCAN node type.
 */
public final class ScanNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.SCAN;
    }

    @Override
    public String displayName() {
        return "Scanner";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "scanner",
                "Evaluates scan criteria against market data and produces scan hits",
                List.of(new NodeTypeDescriptor.EventType(CandleClosed.class, "Candle trigger")),
                List.of(),
                Map.of(
                        "profileId", NodeTypeDescriptor.ConfigField.of("profileId",
                                NodeTypeDescriptor.ConfigField.FieldType.STRING,
                                "Scan Profile", "default"),
                        "triggerInterval", NodeTypeDescriptor.ConfigField.of("triggerInterval",
                                NodeTypeDescriptor.ConfigField.FieldType.STRING,
                                "Trigger Interval", "5m")
                )));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        ScanEngine scanEngine = NodeProviderSupport.typed(
                config, PipelineNodeProvider.CONFIG_KEY_SCAN_ENGINE, ScanEngine.class);
        @SuppressWarnings("unchecked")
        Map<String, ScanProfile> profiles = NodeProviderSupport.typed(
                config, PipelineNodeProvider.CONFIG_KEY_SCAN_PROFILES, Map.class);
        Map<String, ScanProfile> profilesOrEmpty = profiles == null ? Map.of() : profiles;
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "scanner",
                "Evaluates scan criteria against market data and produces scan hits",
                List.of(new NodeTypeDescriptor.EventType(CandleClosed.class, "Candle trigger")),
                List.of(),
                Map.of(
                        "profileId", NodeTypeDescriptor.ConfigField.of("profileId",
                                NodeTypeDescriptor.ConfigField.FieldType.STRING,
                                "Scan Profile", "default"),
                        "triggerInterval", NodeTypeDescriptor.ConfigField.of("triggerInterval",
                                NodeTypeDescriptor.ConfigField.FieldType.STRING,
                                "Trigger Interval", "5m")
                ),
                def -> scanEngine != null
                        ? new ScanNode(scanEngine, profilesOrEmpty)
                        : NoopPipelineNode.create("Scan engine unavailable")));
    }
}
