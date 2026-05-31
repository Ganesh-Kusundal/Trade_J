package com.tradej.optimizer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class MonteCarloOptimizationEngine implements OptimizationEngine {

    private final int seed;

    public MonteCarloOptimizationEngine() {
        this(42);
    }

    public MonteCarloOptimizationEngine(int seed) {
        this.seed = seed;
    }

    @Override
    public OptimizationResult run(OptimizationJob job) {
        Instant start = Instant.now();
        List<TrialResult> allTrials = new ArrayList<>();
        int trialsFailed = 0;
        Random random = new Random(seed);

        int trialsToRun = Math.min(job.maxTrials(), 1000);

        for (int i = 0; i < trialsToRun; i++) {
            Map<String, Object> params = generateRandomParams(job.parameterSpaces(), random);
            try {
                double rawScore = evaluate(job.pipelineDefinitionId(), params, job.objectiveMetric());
                BigDecimal score = BigDecimal.valueOf(rawScore);
                allTrials.add(new TrialResult(
                        UUID.randomUUID(), i + 1, params, score, true, null, Instant.now()
                ));
            } catch (Exception e) {
                trialsFailed++;
                allTrials.add(new TrialResult(
                        UUID.randomUUID(), i + 1, params,
                        BigDecimal.ZERO, false, e.getMessage(), Instant.now()
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

    private Map<String, Object> generateRandomParams(List<ParameterSpace> spaces, Random random) {
        Map<String, Object> params = new HashMap<>();
        for (ParameterSpace space : spaces) {
            params.put(space.name(), sampleFromSpace(space, random));
        }
        return params;
    }

    private Object sampleFromSpace(ParameterSpace space, Random random) {
        if (!space.discreteValues().isEmpty()) {
            return space.discreteValues().get(random.nextInt(space.discreteValues().size()));
        }
        if (space.minValue().isPresent() && space.maxValue().isPresent()) {
            double min = space.minValue().get().doubleValue();
            double max = space.maxValue().get().doubleValue();
            if (space.type() == ParameterType.INTEGER) {
                return random.nextInt((int) (max - min) + 1) + (int) min;
            }
            return min + (max - min) * random.nextDouble();
        }
        return random.nextDouble() * 100;
    }

    private double evaluate(UUID pipelineId, Map<String, Object> params, String metric) {
        return Math.random() * 100;
    }
}