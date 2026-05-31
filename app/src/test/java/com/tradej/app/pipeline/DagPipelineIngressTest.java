package com.tradej.app.pipeline;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.Candle;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.pipeline.runtime.GraphRuntime;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DagPipelineIngressTest {

    @Test
    void dispatchesMatchingCandleClosedToActiveDagGraph() throws Exception {
        VirtualClock clock = new VirtualClock(VirtualClock.Mode.LIVE);
        DagPipelineRuntimeService dagService = new DagPipelineRuntimeService(
                def -> switch (def.type()) {
                    case PipelineNodeTypes.INGRESS -> new com.tradej.pipeline.runtime.IngressNode();
                    case PipelineNodeTypes.SCAN -> noopNode();
                    case PipelineNodeTypes.REACTOR -> new com.tradej.pipeline.reactor.ReactorBridge();
                    default -> throw new IllegalArgumentException("Unexpected node: " + def.type());
                },
                clock,
                null
        );
        DagPipelineIngressBridge bridge = new DagPipelineIngressBridge(dagService);

        PipelineGraph graph = new PipelineGraph(
                "scanner-default",
                "Scanner",
                1,
                List.of(
                        new PipelineNodeDef("ingress-1", PipelineNodeTypes.INGRESS, "Ingress", Map.of(
                                "eventTypes", List.of("CandleClosed"),
                                "intervals", List.of("5m")
                        )),
                        new PipelineNodeDef("scan-1", PipelineNodeTypes.SCAN, "Scan", Map.of("profileId", "p1")),
                        new PipelineNodeDef("reactor-1", PipelineNodeTypes.REACTOR, "Reactor", Map.of())
                ),
                List.of(
                        new PipelineEdgeDef("e1", "ingress-1", "scan-1"),
                        new PipelineEdgeDef("e2", "scan-1", "reactor-1")
                ),
                PipelineExecutionMode.DAG
        );
        dagService.bootstrapGraph(graph);

        CandleClosed event = new CandleClosed(
                EventMetadata.root(),
                new Candle("SBIN", "5m", 1L, 2L, 1L, 2L, 1L, 2L, 10L, true)
        );
        bridge.onEvent(event);
        Thread.sleep(300);

        GraphRuntime runtime = dagService.runtime("scanner-default").orElseThrow();
        PipelineNode ingress = runtime.getExecutionPlan().nodesById().get("ingress-1");
        assertTrue(ingress.getMetrics().processedCount() >= 1L);
    }

    private static PipelineNode noopNode() {
        return new BasePipelineNode() {
            @Override
            protected void onInit() {
            }

            @Override
            protected void processEvent(DomainEvent event) {
            }
        };
    }
}
