package com.tradej.experiments;

import com.tradej.pipeline.platform.PipelineDefinition;
import com.tradej.pipeline.platform.PipelineType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Definition of an experiment instance. */
public record Experiment(
        UUID experimentId,
        String name,
        String hypothesis,
        PipelineType pipelineType,
        UUID pipelineDefinitionId,
        ExperimentStatus status,
        String owner,
        Instant createdAt,
        Instant updatedAt,
        Map<String, String> tags
) {
    public static Experiment create(String name, String hypothesis,
                                    PipelineType type, UUID pipelineDefId, String owner) {
        return new Experiment(
                UUID.randomUUID(), name, hypothesis, type, pipelineDefId,
                ExperimentStatus.DRAFT, owner,
                Instant.now(), Instant.now(), Map.of()
        );
    }

    public Experiment withStatus(ExperimentStatus newStatus) {
        return new Experiment(
                experimentId, name, hypothesis, pipelineType, pipelineDefinitionId,
                newStatus, owner, createdAt, Instant.now(), tags
        );
    }
}
