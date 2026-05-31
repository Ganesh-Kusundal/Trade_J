package com.tradej.app.pipeline;

import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.runtime.GraphRuntime;
import com.tradej.pipeline.runtime.PipelineRuntime;

/**
 * Compiled DAG pipeline instance keyed by graph id.
 * Holds both the {@link GraphRuntime} for ingress routing and a {@link PipelineRuntime}
 * for unified LIVE/REPLAY/BACKTEST lifecycle management.
 */
public final class DagPipelineInstance {

    private final PipelineGraph graph;
    private final PipelineRuntime pipelineRuntime;

    public DagPipelineInstance(PipelineGraph graph, PipelineRuntime pipelineRuntime) {
        this.graph = graph;
        this.pipelineRuntime = pipelineRuntime;
    }

    public PipelineGraph graph() {
        return graph;
    }

    public GraphRuntime runtime() {
        return pipelineRuntime.active();
    }

    public PipelineRuntime pipelineRuntime() {
        return pipelineRuntime;
    }

    public void shutdown() {
        pipelineRuntime.shutdown();
    }
}
