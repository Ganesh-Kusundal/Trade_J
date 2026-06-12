package com.tradej.pipeline.spi.builtin;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.pipeline.reactor.ReactorBridge;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.PipelineNodeProvider;

import java.util.List;
import java.util.Map;

/**
 * Built-in provider for the REACTOR node type.
 */
public final class ReactorNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.REACTOR;
    }

    @Override
    public String displayName() {
        return "Reactor Cold-Path Bridge";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "io",
                "Offloads cold-path events to reactive Flux subscribers",
                List.of(new NodeTypeDescriptor.EventType(CandleClosed.class, "Candle closed")),
                List.of(),
                Map.of()));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        ReactorBridge bridge = NodeProviderSupport.typed(
                config, PipelineNodeProvider.CONFIG_KEY_REACTOR_BRIDGE, ReactorBridge.class);
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "io",
                "Offloads cold-path events to reactive Flux subscribers",
                List.of(new NodeTypeDescriptor.EventType(CandleClosed.class, "Candle closed")),
                List.of(),
                Map.of(),
                def -> bridge));
    }
}
