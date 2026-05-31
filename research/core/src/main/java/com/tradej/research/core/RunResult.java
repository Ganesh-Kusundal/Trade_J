package com.tradej.research.core;

import java.util.Map;

/**
 * Standard performance metrics schemas tied to the config hash and session.
 */
public record RunResult(
    String runId,
    String sessionId,
    String configHash,
    long startTimeMs,
    long endTimeMs,
    long totalTrades,
    double winRate,
    double totalProfitLoss,
    double sharpeRatio,
    double sortinoRatio,
    double maxDrawdown,
    Map<String, Object> additionalMetrics
) {}
