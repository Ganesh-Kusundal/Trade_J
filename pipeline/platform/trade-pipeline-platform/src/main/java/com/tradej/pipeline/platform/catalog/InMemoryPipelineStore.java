package com.tradej.pipeline.platform.catalog;

import com.tradej.pipeline.platform.PipelineDefinition;
import com.tradej.pipeline.platform.PipelineSnapshot;
import com.tradej.pipeline.platform.model.PipelineExecution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryPipelineStore implements PipelineStore {
    private final Map<UUID, PipelineDefinition> definitions = new ConcurrentHashMap<>();
    private final List<PipelineSnapshot> snapshots = new CopyOnWriteArrayList<>();
    private final Map<UUID, PipelineExecution> executions = new ConcurrentHashMap<>();

    @Override
    public synchronized void saveDefinition(PipelineDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    @Override
    public Optional<PipelineDefinition> loadDefinition(UUID definitionId) {
        return Optional.ofNullable(definitions.get(definitionId));
    }

    @Override
    public List<PipelineDefinition> listDefinitions() {
        return new ArrayList<>(definitions.values());
    }

    @Override
    public synchronized void saveSnapshot(PipelineSnapshot snapshot) {
        snapshots.add(snapshot);
    }

    @Override
    public Optional<PipelineSnapshot> loadSnapshot(UUID snapshotId) {
        return snapshots.stream()
                .filter(s -> s.snapshotId().equals(snapshotId))
                .findFirst();
    }

    @Override
    public List<PipelineSnapshot> listSnapshots() {
        return new ArrayList<>(snapshots);
    }

    @Override
    public synchronized void saveExecution(PipelineExecution execution) {
        executions.put(execution.executionId(), execution);
    }

    @Override
    public Optional<PipelineExecution> loadExecution(UUID executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    @Override
    public List<PipelineExecution> listExecutions() {
        return new ArrayList<>(executions.values());
    }
}
