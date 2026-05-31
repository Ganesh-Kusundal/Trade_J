package com.tradej.node;

import java.time.Instant;
import java.util.Map;

/** Output produced by a single node execution. */
public record NodeResult(
        boolean success,
        String outputPortName,
        Object payload,
        Map<String, Object> metrics,
        String errorMessage,
        Instant completedAt
) {
    public static NodeResult ok(String port, Object payload) {
        return new NodeResult(true, port, payload, Map.of(), null, Instant.now());
    }

    public static NodeResult fail(String error) {
        return new NodeResult(false, null, null, Map.of(), error, Instant.now());
    }
}
