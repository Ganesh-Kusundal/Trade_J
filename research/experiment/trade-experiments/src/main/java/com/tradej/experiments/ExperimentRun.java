package com.tradej.experiments;

import com.tradej.pipeline.platform.model.PipelineExecution;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One execution of an experiment. */
public record ExperimentRun(
        UUID runId,
        UUID experimentId,
        int runNumber,
        PipelineExecution execution,
        ExperimentRunStatus status,
        Map<String, Object> parameters,
        Map<String, Object> metrics,
        Instant startedAt,
        Instant finishedAt
) {}
