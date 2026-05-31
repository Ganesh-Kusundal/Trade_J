package com.tradej.pipeline.graph;

/**
 * Definition of a directed edge connecting two pipeline nodes.
 */
public record PipelineEdgeDef(
        String id,
        String source,
        String target
) { }
