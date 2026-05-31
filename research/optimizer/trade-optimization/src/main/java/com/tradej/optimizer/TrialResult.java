package com.tradej.optimizer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Result of a single optimization trial. */
public record TrialResult(
        UUID trialId,
        int trialNumber,
        Map<String, Object> parameterValues,
        BigDecimal objectiveValue,
        boolean success,
        String errorMessage,
        Instant completedAt
) {}
