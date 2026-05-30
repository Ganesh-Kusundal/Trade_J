package com.tradej.pipeline.platform;

/**
 * Lifecycle state of a pipeline definition or execution run.
 */
public enum PipelineStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
