package com.tradej.pipeline.runtime;

import java.util.List;
import java.util.Map;

/**
 * A compiled execution plan containing the topologically sorted list of nodes,
 * node instance lookup map, and an adjacency list representation of routing pathways.
 */
public record ExecutionPlan(
        List<PipelineNode> sortedNodes,
        Map<String, PipelineNode> nodesById,
        Map<String, List<PipelineNode>> routingTable
) { }
