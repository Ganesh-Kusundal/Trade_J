package com.tradej.pipeline.compiler;

import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.runtime.ExecutionPlan;

import java.util.List;

/**
 * Rich result of a graph compilation operation.
 * Contains the original and normalized graphs, the execution plan, and any warnings.
 */
public record CompilationResult(
        PipelineGraph originalGraph,
        ExecutionPlan plan,
        List<CompilationWarning> warnings,
        long compileTimeNs,
        int nodeCount,
        int edgeCount,
        boolean hasCycles,
        boolean hasMultipleEntryPoints
) {
    public record CompilationWarning(String nodeId, String message, WarningLevel level) {
        public enum WarningLevel { INFO, WARN, ERROR }
    }

    public boolean isSuccess() {
        return plan != null;
    }

    public boolean hasErrors() {
        return warnings != null && warnings.stream().anyMatch(w -> w.level() == CompilationWarning.WarningLevel.ERROR);
    }
}
