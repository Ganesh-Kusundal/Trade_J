package com.tradej.core.domain.pipeline;

import java.util.List;

/**
 * Directed graph representing the node-and-edge structure of a pipeline.
 * Immutable value object.
 */
public record PipelineGraph(
        String graphId,
        List<String> nodeIds,
        List<String> edges
) {
    public PipelineGraph {
        if (graphId == null || graphId.isBlank()) {
            throw new IllegalArgumentException("graphId must not be blank");
        }
        nodeIds = List.copyOf(nodeIds == null ? List.of() : nodeIds);
        edges = List.copyOf(edges == null ? List.of() : edges);
    }
}
