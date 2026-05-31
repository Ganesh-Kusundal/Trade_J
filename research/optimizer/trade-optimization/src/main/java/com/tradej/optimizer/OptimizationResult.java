package com.tradej.optimizer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Result of a completed optimization job. */
public record OptimizationResult(
        UUID jobId,
        OptimizationStatus status,
        int trialsCompleted,
        int trialsFailed,
        TrialResult bestTrial,
        List<TrialResult> allTrials,
        Instant startedAt,
        Instant finishedAt,
        String errorSummary
) {}
