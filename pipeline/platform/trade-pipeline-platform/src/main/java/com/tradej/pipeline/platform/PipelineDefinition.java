package com.tradej.pipeline.platform;

import com.tradej.pipeline.graph.PipelineGraph;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable definition of a pipeline within the trading platform.
 * <p>
 * INV-31: Immutability — all fields are set at construction time. Collections
 * and maps are defensively copied to prevent external mutation.
 *
 * @param id          unique identifier
 * @param name        human-readable name
 * @param description textual description
 * @param type        classification of the pipeline
 * @param graph       directed graph of pipeline nodes and edges (from core module)
 * @param version     semantic version
 * @param status      current lifecycle status
 * @param createdAt   creation timestamp (UTC)
 * @param updatedAt   last modification timestamp (UTC)
 * @param metadata    arbitrary key-value metadata
 */
public record PipelineDefinition(
        UUID id,
        String name,
        String description,
        PipelineType type,
        PipelineGraph graph,
        PipelineVersion version,
        PipelineStatus status,
        Instant createdAt,
        Instant updatedAt,
        Map<String, String> metadata
) {
    /**
     * Canonical constructor with validation and defensive copying.
     */
    public PipelineDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
