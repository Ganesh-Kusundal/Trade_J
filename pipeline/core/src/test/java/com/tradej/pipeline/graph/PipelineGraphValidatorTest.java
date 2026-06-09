package com.tradej.pipeline.graph;

import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class PipelineGraphValidatorTest {

    @Test
    void acceptsValidHotPathGraph() {
        PipelineGraph graph = new PipelineGraph(
                "hotpath-default",
                "Test",
                1,
                List.of(
                        new PipelineNodeDef("risk-1", PipelineNodeTypes.RISK, "Risk", Map.of()),
                        new PipelineNodeDef("candle-1", PipelineNodeTypes.CANDLE, "Candle", Map.of()),
                        new PipelineNodeDef("strategy-1", PipelineNodeTypes.STRATEGY, "Strategy", Map.of()),
                        new PipelineNodeDef("execution-1", PipelineNodeTypes.OMS, "OMS", Map.of())
                ),
                List.of(
                        new PipelineEdgeDef("e1", "risk-1", "candle-1"),
                        new PipelineEdgeDef("e2", "candle-1", "strategy-1"),
                        new PipelineEdgeDef("e3", "strategy-1", "execution-1")
                )
        );
        assertDoesNotThrow(() -> PipelineGraphValidator.validateHotPath(graph));
    }

    @Test
    void acceptsValidDagGraph() {
        PipelineGraph graph = new PipelineGraph(
                "scanner-default",
                "Scanner",
                1,
                List.of(
                        new PipelineNodeDef("ingress-1", PipelineNodeTypes.INGRESS, "Ingress", Map.of()),
                        new PipelineNodeDef("scan-1", PipelineNodeTypes.SCAN, "Scan", Map.of("profileId", "p1"))
                ),
                List.of(new PipelineEdgeDef("e1", "ingress-1", "scan-1")),
                PipelineExecutionMode.DAG
        );
        assertDoesNotThrow(() -> PipelineGraphValidator.validateDag(graph));
    }

    @Test
    void rejectsDagGraphWithOms() {
        PipelineGraph graph = new PipelineGraph(
                "bad-dag",
                "Bad",
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
    void rejectsUnknownEdgeTarget() {
        PipelineGraph graph = new PipelineGraph(
                "g1",
                "Test",
                1,
                List.of(new PipelineNodeDef("risk-1", PipelineNodeTypes.RISK, "Risk", Map.of())),
                List.of(new PipelineEdgeDef("e1", "risk-1", "missing"))
        );
        assertThrows(IllegalArgumentException.class, () -> PipelineGraphValidator.validate(graph));
    }

    @Test
    void rejectsScanNodeWithoutProfileId() {
        PipelineGraph graph = new PipelineGraph(
                "g1",
                "Test",
                1,
                List.of(new PipelineNodeDef("scan-1", PipelineNodeTypes.SCAN, "Scan", Map.of())),
                List.of()
        );
        assertThrows(IllegalArgumentException.class, () -> PipelineGraphValidator.validate(graph));
    }
}
