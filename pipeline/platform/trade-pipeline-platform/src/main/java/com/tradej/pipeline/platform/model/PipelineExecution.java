package com.tradej.pipeline.platform.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a single pipeline execution run.
 *
 * <p>
 * Every pipeline invocation — whether a scanner sweep, replay session, backtest,
 * optimization trial, or live trading session — produces exactly one
 * {@link PipelineExecution}. Downstream systems (replay, analytics,
 * optimization, experiments) must use this type for execution tracking.
 *
 * @param executionId      unique identifier for this execution
 * @param pipelineId       id of the {@link com.tradej.pipeline.platform.PipelineDefinition} executed
 * @param pipelineVersion  version at time of execution
 * @param pipelineType     classification of the pipeline
 * @param status           current lifecycle status of the run
 * @param triggeredBy      who or what triggered this run (operator, scheduler, API caller)
 * @param triggeredAt      when the run was triggered (UTC)
 * @param startedAt        when execution actually began (UTC); null until RUNNING
 * @param finishedAt       when execution terminated (UTC); null until terminal state
 * @param durationMs       elapsed wall-clock milliseconds; null until terminal state
 * @param errorSummary     human-readable error message if FAILED; null otherwise
 * @param outputArtifactIds ids of any produced {@link PipelineArtifact}
 * @param metrics          arbitrary execution-level key-value metrics
 * @param metadata         arbitrary key-value metadata
 */
public record PipelineExecution(
        UUID executionId,
        UUID pipelineId,
        com.tradej.pipeline.platform.PipelineVersion pipelineVersion,
        com.tradej.pipeline.platform.PipelineType pipelineType,
        PipelineExecutionStatus status,
        String triggeredBy,
        Instant triggeredAt,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String errorSummary,
        java.util.List<UUID> outputArtifactIds,
        Map<String, Object> metrics,
        Map<String, String> metadata
) {
    public PipelineExecution {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(pipelineId, "pipelineId must not be null");
        Objects.requireNonNull(pipelineVersion, "pipelineVersion must not be null");
        Objects.requireNonNull(pipelineType, "pipelineType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(triggeredBy, "triggeredBy must not be null");
        Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
        outputArtifactIds = List.copyOf(outputArtifactIds == null ? List.of() : outputArtifactIds);
        metrics = Map.copyOf(metrics == null ? Map.of() : metrics);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }

    public boolean isTerminal() {
        return switch (status) {
            case COMPLETED, FAILED, CANCELLED -> true;
            default -> false;
        };
    }

    public boolean hasStarted() {
        return startedAt != null;
    }

    public boolean hasFinished() {
        return isTerminal() && finishedAt != null;
    }
}
