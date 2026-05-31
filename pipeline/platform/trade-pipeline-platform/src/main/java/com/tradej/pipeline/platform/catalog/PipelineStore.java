package com.tradej.pipeline.platform.catalog;

import com.tradej.pipeline.platform.PipelineDefinition;
import com.tradej.pipeline.platform.PipelineSnapshot;
import com.tradej.pipeline.platform.model.PipelineExecution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Backing-store abstraction for PipelineCatalogService. */
public interface PipelineStore {
    void saveDefinition(PipelineDefinition definition);
    Optional<PipelineDefinition> loadDefinition(UUID definitionId);
    List<PipelineDefinition> listDefinitions();
    void saveSnapshot(PipelineSnapshot snapshot);
    Optional<PipelineSnapshot> loadSnapshot(UUID snapshotId);
    List<PipelineSnapshot> listSnapshots();
    void saveExecution(PipelineExecution execution);
    Optional<PipelineExecution> loadExecution(UUID executionId);
    List<PipelineExecution> listExecutions();
}
