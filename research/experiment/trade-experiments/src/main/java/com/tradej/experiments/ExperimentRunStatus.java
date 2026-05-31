package com.tradej.experiments;

/** Lifecycle status for a single ExperimentRun. */
enum ExperimentRunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
