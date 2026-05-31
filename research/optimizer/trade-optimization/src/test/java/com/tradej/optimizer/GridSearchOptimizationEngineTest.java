package com.tradej.optimizer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class GridSearchOptimizationEngineTest {

    @Test
    void gridSearchRunsAndReturnsResults() {
        GridSearchOptimizationEngine engine = new GridSearchOptimizationEngine();

        OptimizationJob job = new OptimizationJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OptimizationStrategy.GRID_SEARCH,
                List.of(new ParameterSpace(
                        "param1",
                        ParameterType.STRING,
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        List.of("a", "b", "c")
                )),
                "metric",
                true,
                10,
                Map.of()
        );

        OptimizationResult result = engine.run(job);

        assertEquals(OptimizationStatus.COMPLETED, result.status());
        assertTrue(result.trialsCompleted() > 0);
        assertNotNull(result.bestTrial());
        assertTrue(result.bestTrial().success());
        assertFalse(result.allTrials().isEmpty());
    }

    @Test
    void gridSearchRespectsMaxTrials() {
        GridSearchOptimizationEngine engine = new GridSearchOptimizationEngine();

        OptimizationJob job = new OptimizationJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OptimizationStrategy.GRID_SEARCH,
                List.of(new ParameterSpace(
                        "param1",
                        ParameterType.STRING,
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        List.of("a", "b", "c", "d", "e", "f", "g", "h")
                )),
                "metric",
                true,
                3,
                Map.of()
        );

        OptimizationResult result = engine.run(job);

        assertEquals(3, result.trialsCompleted());
    }

    @Test
    void gridSearchFindsBestTrial() {
        GridSearchOptimizationEngine engine = new GridSearchOptimizationEngine();

        OptimizationJob job = new OptimizationJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OptimizationStrategy.GRID_SEARCH,
                List.of(new ParameterSpace(
                        "param1",
                        ParameterType.STRING,
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        List.of("value1", "value2")
                )),
                "metric",
                true,
                10,
                Map.of()
        );

        OptimizationResult result = engine.run(job);

        assertNotNull(result.bestTrial());
        assertTrue(result.bestTrial().objectiveValue().compareTo(BigDecimal.ZERO) >= 0);
    }
}