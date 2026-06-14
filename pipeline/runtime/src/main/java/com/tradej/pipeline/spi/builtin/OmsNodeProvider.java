package com.tradej.pipeline.spi.builtin;

import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.execution.node.OmsNode;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.PipelineNodeProvider;

import java.util.List;
import java.util.Map;

/**
 * Built-in provider for the OMS node type.
 */
public final class OmsNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.OMS;
    }

    @Override
    public String displayName() {
        return "Order Execution";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "oms",
                "Places orders, manages OMS lifecycle, handles fills",
                List.of(new NodeTypeDescriptor.EventType(SignalPendingExecution.class, "Approved signal")),
                List.of(
                        new NodeTypeDescriptor.EventType(OrderAccepted.class, "Order accepted"),
                        new NodeTypeDescriptor.EventType(OrderFilled.class, "Order filled"),
                        new NodeTypeDescriptor.EventType(OrderRejected.class, "Order rejected")
                ),
                Map.of()));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        ExecutionHandler handler = NodeProviderSupport.typed(
                config, PipelineNodeProvider.CONFIG_KEY_EXECUTION_HANDLER, ExecutionHandler.class);
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "oms",
                "Places orders, manages OMS lifecycle, handles fills",
                List.of(new NodeTypeDescriptor.EventType(SignalPendingExecution.class, "Approved signal")),
                List.of(
                        new NodeTypeDescriptor.EventType(OrderAccepted.class, "Order accepted"),
                        new NodeTypeDescriptor.EventType(OrderFilled.class, "Order filled"),
                        new NodeTypeDescriptor.EventType(OrderRejected.class, "Order rejected")
                ),
                Map.of(),
                def -> new OmsNode(handler)));
    }
}
