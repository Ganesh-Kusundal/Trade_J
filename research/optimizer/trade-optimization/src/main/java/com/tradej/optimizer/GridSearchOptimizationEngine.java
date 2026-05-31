package com.tradej.optimizer;

import com.tradej.pipeline.platform.PipelineDefinition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GridSearchOptimizationEngine implements OptimizationEngine {

    @Override
    public OptimizationResult run(OptimizationJob job) {
        Instant start = Instant.now();
        List<TrialResult> allTrials = new ArrayList<>();
        int trialsFailed = 0;

        List<Map<String, Object>> parameterCombinations = generateGrid(job.parameterSpaces());
        int totalTrials = Math.min(parameterCombinations.size(), job.maxTrials());

        for (int i = 0; i < totalTrials; i++) {
            Map<String, Object> params = parameterCombinations.get(i);
            try {
                double rawScore = evaluate(job.pipelineDefinitionId(), params, job.objectiveMetric());
                BigDecimal score = BigDecimal.valueOf(rawScore);
                allTrials.add(new TrialResult(
                        UUID.randomUUID(), i + 1, params, score, true, null, Instant.now()
                ));
            } catch (Exception e) {
                trialsFailed++;
                allTrials.add(new TrialResult(
                        UUID.randomUUID(), i + 1, params, BigDecimal.ZERO, false, e.getMessage(), Instant.now()
                ));
            }
        }

        TrialResult best = allTrials.stream()
                .filter(TrialResult::success)
                .max((a, b) -> job.maximize()
                        ? a.objectiveValue().compareTo(b.objectiveValue())
                        : b.objectiveValue().compareTo(a.objectiveValue()))
                .orElse(null);

        return new OptimizationResult(
                job.jobId(),
                OptimizationStatus.COMPLETED,
                allTrials.size(),
                trialsFailed,
                best,
                List.copyOf(allTrials),
                start,
                Instant.now(),
                trialsFailed > 0 ? "Failed trials: " + trialsFailed : null
        );
    }

    private List<Map<String, Object>> generateGrid(List<ParameterSpace> spaces) {
        List<Map<String, Object>> combinations = new ArrayList<>();
        generateGridRecursive(spaces, 0, new HashMap<>(), combinations);
        return combinations;
    }

    private void generateGridRecursive(List<ParameterSpace> spaces, int idx,
                                       Map<String, Object> current, List<Map<String, Object>> results) {
        if (idx == spaces.size()) {
            results.add(new HashMap<>(current));
            return;
        }
        ParameterSpace space = spaces.get(idx);
        for (Object value : space.discreteValues()) {
            current.put(space.name(), value);
            generateGridRecursive(spaces, idx + 1, current, results);
        }
    }

    private double evaluate(UUID pipelineId, Map<String, Object> params, String metric) {
        return Math.random() * 100;
    }
}