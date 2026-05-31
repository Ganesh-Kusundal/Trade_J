package com.tradej.optimizer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MonteCarloOptimizationEngineTest {

    @Test
    void monteCarloRunsWithDefaultSeed() {
        MonteCarloOptimizationEngine engine = new MonteCarloOptimizationEngine();

        OptimizationJob job = new OptimizationJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OptimizationStrategy.MONTE_CARLO,
                List.of(new ParameterSpace(
                        "param1",
                        ParameterType.DOUBLE,
                        java.util.Optional.of(BigDecimal.valueOf(0.0)),
                        java.util.Optional.of(BigDecimal.valueOf(1.0)),
                        java.util.Optional.empty(),
                        List.of()
                )),
                "metric",
                true,
                50,
                Map.of()
        );

        OptimizationResult result = engine.run(job);

        assertEquals(OptimizationStatus.COMPLETED, result.status());
        assertEquals(50, result.trialsCompleted());
        assertFalse(result.allTrials().isEmpty());
    }

    @Test
    void monteCarloRunsWithCustomSeed() {
        MonteCarloOptimizationEngine engine = new MonteCarloOptimizationEngine(12345);

        OptimizationJob job = new OptimizationJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OptimizationStrategy.MONTE_CARLO,
                List.of(new ParameterSpace(
                        "param1",
                        ParameterType.INTEGER,
                        java.util.Optional.of(BigDecimal.valueOf(1)),
                        java.util.Optional.of(BigDecimal.valueOf(10)),
                        java.util.Optional.empty(),
                        List.of()
                )),
                "metric",
                true,
                20,
                Map.of()
        );

        OptimizationResult result = engine.run(job);

        assertEquals(OptimizationStatus.COMPLETED, result.status());
        assertEquals(20, result.trialsCompleted());
    }

    @Test
    void monteCarloReturnsBestTrial() {
        MonteCarloOptimizationEngine engine = new MonteCarloOptimizationEngine(42);

        OptimizationJob job = new OptimizationJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OptimizationStrategy.MONTE_CARLO,
                List.of(new ParameterSpace(
                        "param1",
                        ParameterType.STRING,
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        List.of("x", "y", "z")
                )),
                "metric",
                true,
                30,
                Map.of()
        );

        OptimizationResult result = engine.run(job);

        assertNotNull(result.bestTrial());
        assertTrue(result.bestTrial().success());
        assertTrue(result.bestTrial().objectiveValue().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void monteCarloRespectsMaxTrials() {
        MonteCarloOptimizationEngine engine = new MonteCarloOptimizationEngine();

        OptimizationJob job = new OptimizationJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OptimizationStrategy.MONTE_CARLO,
                List.of(new ParameterSpace(
                        "param1",
                        ParameterType.DOUBLE,
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        List.of()
                )),
                "metric",
                true,
                5,
                Map.of()
        );

        OptimizationResult result = engine.run(job);

        assertEquals(5, result.trialsCompleted());
    }
}