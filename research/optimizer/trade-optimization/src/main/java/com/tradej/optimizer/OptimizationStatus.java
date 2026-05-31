package com.tradej.optimizer;

/** Lifecycle status for an optimization job. */
enum OptimizationStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
