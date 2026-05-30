package com.tradej.pipeline.platform;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable point-in-time capture of a pipeline's state.
 * <p>
 * Snapshots are used for audit trails, rollback, and replay scenarios.
 *
 * @param snapshotId       unique snapshot identifier
 * @param pipelineId       id of the pipeline definition this snapshot captures
 * @param version          pipeline version at time of capture
 * @param status           pipeline status at time of capture
 * @param capturedAt       when the snapshot was taken (UTC)
 * @param snapshotMetadata arbitrary key-value snapshot metadata
 */
public record PipelineSnapshot(
        UUID snapshotId,
        UUID pipelineId,
        PipelineVersion version,
        PipelineStatus status,
        Instant capturedAt,
        Map<String, String> snapshotMetadata
) {
    /**
     * Canonical constructor with validation and defensive copying.
     */
    public PipelineSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(pipelineId, "pipelineId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        snapshotMetadata = Map.copyOf(snapshotMetadata == null ? Map.of() : snapshotMetadata);
    }
}
