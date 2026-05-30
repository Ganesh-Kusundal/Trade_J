package com.tradej.pipeline.platform;

import com.tradej.core.domain.pipeline.PipelineGraph;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reusable template for creating {@link PipelineDefinition} instances.
 * <p>
 * Templates capture the default structure, type, and metadata for a class of pipelines.
 * They can be instantiated to produce concrete pipeline definitions.
 *
 * @param templateId unique template identifier
 * @param name       template name
 * @param description template description
 * @param type       pipeline type this template produces
 * @param graph      default graph structure
 * @param version    template version
 * @param defaults   default metadata values applied to instantiated definitions
 */
public record PipelineTemplate(
        UUID templateId,
        String name,
        String description,
        PipelineType type,
        PipelineGraph graph,
        PipelineVersion version,
        Map<String, String> defaults
) {
    /**
     * Canonical constructor with validation and defensive copying.
     */
    public PipelineTemplate {
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(version, "version must not be null");
        defaults = Map.copyOf(defaults == null ? Map.of() : defaults);
    }

    /**
     * Creates a new {@link PipelineDefinition} from this template.
     *
     * @return a new DRAFT pipeline definition using this template's structure
     */
    public PipelineDefinition instantiate() {
        Instant now = Instant.now();
        return new PipelineDefinition(
                UUID.randomUUID(),
                name,
                description,
                type,
                graph,
                version,
                PipelineStatus.DRAFT,
                now,
                now,
                defaults
        );
    }
}
