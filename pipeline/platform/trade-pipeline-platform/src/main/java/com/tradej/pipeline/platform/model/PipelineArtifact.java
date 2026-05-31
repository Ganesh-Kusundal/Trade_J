package com.tradej.pipeline.platform.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable description of an artifact produced by a pipeline execution.
 *
 * <p>Typical artifacts: equity-curve CSV, performance JSON, scan-hit table,
 * optimization sweep report, ML model checkpoint.
 */
public record PipelineArtifact(
        UUID artifactId,
        UUID executionId,
        ArtifactType artifactType,
        String contentType,
        long sizeBytes,
        String storageUri,
        Map<String, String> metadata,
        Instant createdAt
) {
    public PipelineArtifact {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(artifactType, "artifactType must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }

    public enum ArtifactType {
        EQUITY_CURVE,
        PERFORMANCE_REPORT,
        SCAN_HITS,
        TRADE_LOG,
        OPTIMIZATION_REPORT,
        MODEL_CHECKPOINT,
        METRICS_JSON,
        CUSTOM
    }
}
