package com.tradej.node;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Runtime context supplied to every node execution. */
public record NodeContext(
        UUID executionId,
        String triggeredBy,
        Instant now,
        Map<String, Object> sharedState
) {
}
