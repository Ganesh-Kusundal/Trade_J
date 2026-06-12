package com.tradej.pipeline.spi.builtin;

import com.tradej.execution.node.RiskNode;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.PipelineNodeProvider;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;

import java.util.List;
import java.util.Map;

/**
 * Built-in provider for the RISK node type.
 */
public final class RiskNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.RISK;
    }

    @Override
    public String displayName() {
        return "Pre-Trade Risk";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "risk",
                "Validates pre-trade risk limits (kill switch, daily loss, max positions)",
                List.of(new NodeTypeDescriptor.EventType(SignalGenerated.class, "Incoming signal")),
                List.of(new NodeTypeDescriptor.EventType(SignalPendingExecution.class, "Approved signal")),
                Map.of()));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        PositionRiskHandler handler = NodeProviderSupport.typed(
                config, PipelineNodeProvider.CONFIG_KEY_POSITION_RISK_HANDLER, PositionRiskHandler.class);
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "risk",
                "Validates pre-trade risk limits (kill switch, daily loss, max positions)",
                List.of(new NodeTypeDescriptor.EventType(SignalGenerated.class, "Incoming signal")),
                List.of(new NodeTypeDescriptor.EventType(SignalPendingExecution.class, "Approved signal")),
                Map.of(),
                def -> new RiskNode(handler)));
    }
}
