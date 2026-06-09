package com.tradej.pipeline.graph;

import java.util.List;

/**
 * A declarative representation of the entire trading pipeline,
 * compiled from a JSON layout (e.g. from React Flow) and matching
 * topological execution structures.
 */
public record PipelineGraph(
        String id,
        String name,
        int version,
        List<PipelineNodeDef> nodes,
        List<PipelineEdgeDef> edges,
        PipelineExecutionMode executionMode
) {
    public PipelineGraph(String id, String name, int version, List<PipelineNodeDef> nodes, List<PipelineEdgeDef> edges) {
        this(id, name, version, nodes, edges, null);
    }

    public PipelineGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        executionMode = executionMode == null ? inferExecutionMode(id) : executionMode;
    }

    private static PipelineExecutionMode inferExecutionMode(String graphId) {
        if (graphId == null) {
            return PipelineExecutionMode.HOT_PATH;
        }
        return switch (graphId) {
            case "scanner-default", "scanner-tick-default" -> PipelineExecutionMode.DAG;
            default -> PipelineExecutionMode.HOT_PATH;
        };
    }
}
