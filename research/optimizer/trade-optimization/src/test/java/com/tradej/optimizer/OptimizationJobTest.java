package com.tradej.optimizer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OptimizationJobTest {

    @Test
    void optimizationJobConstruction() {
        UUID jobId = UUID.randomUUID();
        UUID pipelineDefId = UUID.randomUUID();

        ParameterSpace param1 = new ParameterSpace(
                "learningRate",
                ParameterType.DOUBLE,
                Optional.of(BigDecimal.valueOf(0.001)),
                Optional.of(BigDecimal.valueOf(0.1)),
                Optional.of(BigDecimal.valueOf(0.01)),
                List.of()
        );

        OptimizationJob job = new OptimizationJob(
                jobId,
                pipelineDefId,
                OptimizationStrategy.GRID_SEARCH,
                List.of(param1),
                "sharpeRatio",
                true,
                100,
                Map.of("version", "1.0")
        );

        assertEquals(jobId, job.jobId());
        assertEquals(pipelineDefId, job.pipelineDefinitionId());
        assertEquals(OptimizationStrategy.GRID_SEARCH, job.strategy());
        assertEquals(1, job.parameterSpaces().size());
        assertEquals("learningRate", job.parameterSpaces().get(0).name());
        assertEquals("sharpeRatio", job.objectiveMetric());
        assertTrue(job.maximize());
        assertEquals(100, job.maxTrials());
        assertEquals("1.0", job.metadata().get("version"));
    }

    @Test
    void parameterSpaceContinuous() {
        ParameterSpace space = new ParameterSpace(
                "windowSize",
                ParameterType.INTEGER,
                Optional.of(BigDecimal.valueOf(5)),
                Optional.of(BigDecimal.valueOf(100)),
                Optional.of(BigDecimal.valueOf(5)),
                List.of()
        );

        assertEquals("windowSize", space.name());
        assertEquals(ParameterType.INTEGER, space.type());
        assertEquals(BigDecimal.valueOf(5), space.minValue().orElseThrow());
        assertEquals(BigDecimal.valueOf(100), space.maxValue().orElseThrow());
        assertEquals(BigDecimal.valueOf(5), space.step().orElseThrow());
        assertTrue(space.discreteValues().isEmpty());
    }

    @Test
    void parameterSpaceDiscrete() {
        ParameterSpace space = new ParameterSpace(
                "strategy",
                ParameterType.STRING,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of("momentum", "meanReversion", "breakout")
        );

        assertEquals(ParameterType.STRING, space.type());
        assertTrue(space.minValue().isEmpty());
        assertTrue(space.discreteValues().contains("momentum"));
        assertTrue(space.discreteValues().contains("meanReversion"));
        assertEquals(3, space.discreteValues().size());
    }
}