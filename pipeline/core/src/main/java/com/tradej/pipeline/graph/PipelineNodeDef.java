package com.tradej.pipeline.graph;

import java.util.Map;

/**
 * Definition of a node in the pipeline execution graph.
 */
public record PipelineNodeDef(
        String id,
        String type,
        String label,
        Map<String, Object> config
) { }
