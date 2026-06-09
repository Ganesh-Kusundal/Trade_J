package com.tradej.pipeline.graph;

/**
 * Determines how a {@link PipelineGraph} is compiled and executed at runtime.
 */
public enum PipelineExecutionMode {
    /** Linear sequential execution on the Disruptor hot path. */
    HOT_PATH,
    /** Edge-routed DAG execution via ingress nodes and EventBus subscription. */
    DAG
}
