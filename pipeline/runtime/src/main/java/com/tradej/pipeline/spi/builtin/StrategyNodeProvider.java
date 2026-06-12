package com.tradej.pipeline.spi.builtin;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.PipelineNodeProvider;
import com.tradej.strategy.node.StrategyNode;
import com.tradej.strategy.service.GraphStrategySandbox;

import java.util.List;
import java.util.Map;

/**
 * Built-in provider for the STRATEGY node type.
 */
public final class StrategyNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.STRATEGY;
    }

    @Override
    public String displayName() {
        return "Strategy Engine";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "signal",
                "Evaluates registered StrategyPlugin instances against candle closes",
                List.of(new NodeTypeDescriptor.EventType(CandleClosed.class, "Completed candle")),
                List.of(new NodeTypeDescriptor.EventType(SignalGenerated.class, "Generated signal")),
                Map.of()));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        GraphStrategySandbox sandbox = NodeProviderSupport.typed(
                config, PipelineNodeProvider.CONFIG_KEY_GRAPH_STRATEGY_SANDBOX, GraphStrategySandbox.class);
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "signal",
                "Evaluates registered StrategyPlugin instances against candle closes",
                List.of(new NodeTypeDescriptor.EventType(CandleClosed.class, "Completed candle")),
                List.of(new NodeTypeDescriptor.EventType(SignalGenerated.class, "Generated signal")),
                Map.of(),
                def -> new StrategyNode(sandbox)));
    }
}
