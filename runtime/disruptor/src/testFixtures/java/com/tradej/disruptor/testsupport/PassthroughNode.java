package com.tradej.disruptor.testsupport;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;

import java.util.List;
import java.util.Map;

/**
 * Forwards every inbound event to the hot-path publisher without transformation.
 */
public final class PassthroughNode extends BasePipelineNode {

    @Override
    protected void onInit() {
        // no-op
    }

    @Override
    protected void processEvent(DomainEvent event) {
        context.publish(event);
    }

    public static PipelineGraph passthroughGraph() {
        return new PipelineGraph(
                "passthrough",
                "Passthrough",
                1,
                List.of(new PipelineNodeDef("pass-1", PipelineNodeTypes.INGRESS, "Passthrough", Map.of())),
                List.of()
        );
    }

    public static TestPipelineGraphBridge passthroughBridge() {
        return new TestPipelineGraphBridge(passthroughGraph(), def -> new PassthroughNode());
    }
}
