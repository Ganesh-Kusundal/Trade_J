package com.tradej.disruptor.testsupport;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.execution.node.OmsNode;
import com.tradej.execution.node.RiskNode;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.ExecutionPlan;
import com.tradej.pipeline.runtime.GraphCompiler;
import com.tradej.pipeline.runtime.GraphRuntime;
import com.tradej.pipeline.runtime.PipelineContext;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.runtime.PipelineRuntimeBridge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Test {@link PipelineRuntimeBridge} that compiles a minimal risk → OMS graph
 * for component tests requiring the Config A Disruptor pipeline.
 */
public final class TestPipelineGraphBridge implements PipelineRuntimeBridge {

    private final Function<PipelineNodeDef, PipelineNode> nodeFactory;
    private final AtomicReference<GraphRuntime> runtimeRef = new AtomicReference<>();
    private volatile PipelineGraph activeGraph;

    public TestPipelineGraphBridge(PositionRiskHandler riskHandler, ExecutionHandler executionHandler) {
        this.activeGraph = riskToOmsGraph();
        this.nodeFactory = def -> switch (def.type()) {
            case PipelineNodeTypes.RISK -> new RiskNode(riskHandler);
            case PipelineNodeTypes.OMS -> new OmsNode(executionHandler);
            default -> throw new IllegalArgumentException("Unsupported node type: " + def.type());
        };
    }

    public TestPipelineGraphBridge(
            PipelineGraph graph,
            Function<PipelineNodeDef, PipelineNode> nodeFactory
    ) {
        this.activeGraph = graph;
        this.nodeFactory = nodeFactory;
    }

    @Override
    public void compileHotPath(Consumer<DomainEvent> hotPathPublisher) {
        reload(activeGraph, hotPathPublisher);
    }

    @Override
    public void reload(PipelineGraph graph, Consumer<DomainEvent> hotPathPublisher) {
        GraphCompiler compiler = new GraphCompiler(nodeFactory);
        PipelineContext context = new PipelineContext() {
            @Override
            public void publish(DomainEvent event) {
                // events published by graph nodes via injected publisher
            }

            @Override
            public long getClockTimeMs() {
                return System.currentTimeMillis();
            }

            @Override
            public <T> Optional<T> getService(Class<T> serviceType) {
                return Optional.empty();
            }
        };
        ExecutionPlan plan = compiler.compile(graph, context, hotPathPublisher);
        runtimeRef.set(new GraphRuntime(plan, graph));
        activeGraph = graph;
    }

    @Override
    public AtomicReference<GraphRuntime> runtimeRef() {
        return runtimeRef;
    }

    @Override
    public PipelineGraph activeGraph() {
        return activeGraph;
    }

    public static PipelineGraph riskToOmsGraph() {
        return new PipelineGraph(
                "test-risk-oms",
                "Test Risk → OMS",
                1,
                List.of(
                        new PipelineNodeDef("risk-1", PipelineNodeTypes.RISK, "Risk", Map.of()),
                        new PipelineNodeDef("oms-1", PipelineNodeTypes.OMS, "OMS", Map.of())
                ),
                List.of(new PipelineEdgeDef("e1", "risk-1", "oms-1"))
        );
    }
}
