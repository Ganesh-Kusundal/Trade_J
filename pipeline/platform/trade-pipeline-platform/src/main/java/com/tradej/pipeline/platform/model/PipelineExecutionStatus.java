package com.tradej.pipeline.platform.model;

/**
 * Lifecycle state of a {@link com.tradej.pipeline.platform.PipelineExecution} run.
 *
 * <p>Mirrors {@link com.tradej.pipeline.platform.PipelineStatus} for definitions,
 * but covers execution-only terminal states such as FAILED and CANCELLED that
 * do not apply to static definitions.
 */
public enum PipelineExecutionStatus {
    QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, PAUSED
}
