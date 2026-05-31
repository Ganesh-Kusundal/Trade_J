package com.tradej.optimizer;

import com.tradej.pipeline.platform.PipelineDefinition;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Declares what to optimize over a pipeline definition. */
public record OptimizationJob(
        UUID jobId,
        UUID pipelineDefinitionId,
        OptimizationStrategy strategy,
        List<ParameterSpace> parameterSpaces,
        String objectiveMetric,
        boolean maximize,
        int maxTrials,
        Map<String, String> metadata
) {}
