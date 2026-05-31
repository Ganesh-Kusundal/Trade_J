package com.tradej.app.pipeline;

import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineGraphValidator;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class PipelineCompileRoutingTest {

    @Test
    void validatesScannerDagGraph() {
        PipelineGraph graph = PipelineRuntimeService.defaultScannerGraph("test-profile");
        assertDoesNotThrow(() -> PipelineGraphValidator.validateDag(graph));
    }

    @Test
    void rejectsOmsNodeInDagGraph() {
        PipelineGraph graph = new PipelineGraph(
                "invalid-dag",
                "Invalid",
                1,
                List.of(
                        new PipelineNodeDef("ingress-1", PipelineNodeTypes.INGRESS, "Ingress", Map.of()),
                        new PipelineNodeDef("execution-1", PipelineNodeTypes.OMS, "OMS", Map.of())
                ),
                List.of(new PipelineEdgeDef("e1", "ingress-1", "execution-1")),
                PipelineExecutionMode.DAG
        );
        assertThrows(IllegalArgumentException.class, () -> PipelineGraphValidator.validateDag(graph));
    }

    @Test
    void hotPathServiceRejectsDagGraph() {
        PipelineGraph dagGraph = PipelineRuntimeService.defaultScannerGraph("test-profile");
        PipelineRuntimeService service = new PipelineRuntimeService(
                null,
                new com.tradej.pipeline.clock.VirtualClock(com.tradej.pipeline.clock.VirtualClock.Mode.LIVE),
                null,
                new com.tradej.app.pipeline.reactor.ReactorBridgeMetrics(),
                new com.tradej.pipeline.reactor.ReactorBridge()
        );
        assertThrows(IllegalArgumentException.class, () -> service.reload(dagGraph, event -> {}));
    }
}
