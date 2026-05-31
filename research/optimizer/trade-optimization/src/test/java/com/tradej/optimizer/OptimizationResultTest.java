package com.tradej.optimizer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OptimizationResultTest {

    @Test
    void successfulOptimizationResult() {
        UUID jobId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant finish = Instant.now().plusSeconds(120);

        TrialResult bestTrial = new TrialResult(
                UUID.randomUUID(),
                5,
                Map.of("lr", 0.01),
                BigDecimal.valueOf(2.1),
                true,
                null,
                finish
        );

        TrialResult trial1 = new TrialResult(
                UUID.randomUUID(),
                1,
                Map.of("lr", 0.001),
                BigDecimal.valueOf(1.5),
                true,
                null,
                finish
        );

        OptimizationResult result = new OptimizationResult(
                jobId,
                OptimizationStatus.COMPLETED,
                10,
                0,
                bestTrial,
                List.of(trial1, bestTrial),
                start,
                finish,
                null
        );

        assertEquals(jobId, result.jobId());
        assertEquals(OptimizationStatus.COMPLETED, result.status());
        assertEquals(10, result.trialsCompleted());
        assertEquals(0, result.trialsFailed());
        assertEquals(bestTrial, result.bestTrial());
        assertEquals(2, result.allTrials().size());
        assertEquals(BigDecimal.valueOf(2.1), result.bestTrial().objectiveValue());
        assertNull(result.errorSummary());
    }

    @Test
    void failedOptimizationResult() {
        UUID jobId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant finish = Instant.now().plusSeconds(60);

        OptimizationResult result = new OptimizationResult(
                jobId,
                OptimizationStatus.FAILED,
                5,
                3,
                null,
                List.of(),
                start,
                finish,
                "3 trials failed: timeout, invalid params, broker error"
        );

        assertEquals(OptimizationStatus.FAILED, result.status());
        assertEquals(5, result.trialsCompleted());
        assertEquals(3, result.trialsFailed());
        assertNull(result.bestTrial());
        assertTrue(result.errorSummary().contains("timeout"));
    }

    @Test
    void optimizationStrategiesAreComplete() {
        assertEquals(4, OptimizationStrategy.values().length);
        assertNotNull(OptimizationStrategy.GRID_SEARCH);
        assertNotNull(OptimizationStrategy.WALK_FORWARD);
        assertNotNull(OptimizationStrategy.MONTE_CARLO);
        assertNotNull(OptimizationStrategy.PARAMETER_SWEEP);
    }

    @Test
    void optimizationStatusTransitions() {
        assertEquals(5, OptimizationStatus.values().length);
        assertNotNull(OptimizationStatus.QUEUED);
        assertNotNull(OptimizationStatus.RUNNING);
        assertNotNull(OptimizationStatus.COMPLETED);
        assertNotNull(OptimizationStatus.FAILED);
        assertNotNull(OptimizationStatus.CANCELLED);
    }
}