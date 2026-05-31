package com.tradej.optimizer;

import com.tradej.pipeline.platform.PipelineDefinition;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pluggable optimization engine contract. */
public interface OptimizationEngine {

    OptimizationResult run(OptimizationJob job);
}
