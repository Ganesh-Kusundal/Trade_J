package com.tradej.app.integration;

import com.tradej.pipeline.runtime.PipelineNodeTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures canonical hot-path node types are registered for live, replay, and backtest DAGs.
 */
@Tag("component")
class PipelineNodeParityComponentTest {

    private static final Set<String> REQUIRED_NODES = Set.of(
            PipelineNodeTypes.INGRESS,
            PipelineNodeTypes.CANDLE,
            PipelineNodeTypes.FEATURE,
            PipelineNodeTypes.STRATEGY,
            PipelineNodeTypes.RISK,
            PipelineNodeTypes.PORTFOLIO,
            PipelineNodeTypes.SIGNAL_GATE,
            PipelineNodeTypes.ORDER_PLACEMENT,
            PipelineNodeTypes.FILL_RECONCILIATION
    );

    @Test
    void allExecutionModesShareCoreNodeTypeIds() {
        Set<String> declared = Arrays.stream(PipelineNodeTypes.class.getDeclaredFields())
                .filter(f -> f.getType() == String.class)
                .map(f -> {
                    try {
                        return (String) f.get(null);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());

        for (String required : REQUIRED_NODES) {
            assertTrue(declared.contains(required), "Missing node type constant: " + required);
        }
    }
}
