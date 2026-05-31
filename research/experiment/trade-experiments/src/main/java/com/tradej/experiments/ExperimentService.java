package com.tradej.experiments;

import com.tradej.pipeline.platform.model.PipelineExecution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ExperimentService {

    public enum LifecycleEvent {
        CREATED, STATUS_CHANGED, RUN_STARTED, RUN_COMPLETED, RUN_FAILED, DELETED
    }

    private final Map<UUID, Experiment> experiments = new ConcurrentHashMap<>();
    private final Map<UUID, List<ExperimentRun>> runs = new ConcurrentHashMap<>();
    private final List<Consumer<Experiment>> experimentListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Consumer<RunEvent>> runListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public Experiment create(String name, String hypothesis, com.tradej.pipeline.platform.PipelineType pipelineType,
                             UUID pipelineDefinitionId, String owner) {
        Experiment experiment = Experiment.create(name, hypothesis, pipelineType, pipelineDefinitionId, owner);
        experiments.put(experiment.experimentId(), experiment);
        notifyExperimentListeners(experiment, LifecycleEvent.CREATED);
        return experiment;
    }

    public Optional<Experiment> get(UUID experimentId) {
        return Optional.ofNullable(experiments.get(experimentId));
    }

    public List<Experiment> getAll() {
        return List.copyOf(experiments.values());
    }

    public List<Experiment> getByStatus(ExperimentStatus status) {
        return experiments.values().stream()
                .filter(e -> e.status() == status)
                .toList();
    }

    public List<Experiment> getByOwner(String owner) {
        return experiments.values().stream()
                .filter(e -> e.owner().equals(owner))
                .toList();
    }

    public Experiment updateStatus(UUID experimentId, ExperimentStatus newStatus) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment == null) {
            throw new IllegalArgumentException("Experiment not found: " + experimentId);
        }
        Experiment updated = experiment.withStatus(newStatus);
        experiments.put(experimentId, updated);
        notifyExperimentListeners(updated, LifecycleEvent.STATUS_CHANGED);
        return updated;
    }

    public void delete(UUID experimentId) {
        Experiment experiment = experiments.remove(experimentId);
        if (experiment != null) {
            runs.remove(experimentId);
            notifyExperimentListeners(experiment, LifecycleEvent.DELETED);
        }
    }

    public ExperimentRun startRun(UUID experimentId, Map<String, Object> parameters) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment == null) {
            throw new IllegalArgumentException("Experiment not found: " + experimentId);
        }

        List<ExperimentRun> existingRuns = runs.computeIfAbsent(experimentId, k -> new ArrayList<>());
        int runNumber = existingRuns.size() + 1;

        ExperimentRun run = new ExperimentRun(
                UUID.randomUUID(),
                experimentId,
                runNumber,
                null,
                ExperimentRunStatus.RUNNING,
                parameters,
                Map.of(),
                Instant.now(),
                null
        );
        existingRuns.add(run);
        updateStatus(experimentId, ExperimentStatus.RUNNING);
        notifyRunListeners(run, LifecycleEvent.RUN_STARTED);
        return run;
    }

    public void completeRun(UUID runId, Map<String, Object> metrics) {
        ExperimentRun run = findRun(runId);
        if (run == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }

        ExperimentRun updated = new ExperimentRun(
                run.runId(),
                run.experimentId(),
                run.runNumber(),
                run.execution(),
                ExperimentRunStatus.COMPLETED,
                run.parameters(),
                metrics,
                run.startedAt(),
                Instant.now()
        );
        replaceRun(updated);
        notifyRunListeners(updated, LifecycleEvent.RUN_COMPLETED);

        if (allRunsComplete(run.experimentId())) {
            updateStatus(run.experimentId(), ExperimentStatus.COMPLETED);
        }
    }

    public void failRun(UUID runId, String errorMessage) {
        ExperimentRun run = findRun(runId);
        if (run == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }

        ExperimentRun updated = new ExperimentRun(
                run.runId(),
                run.experimentId(),
                run.runNumber(),
                run.execution(),
                ExperimentRunStatus.FAILED,
                run.parameters(),
                Map.of("error", errorMessage),
                run.startedAt(),
                Instant.now()
        );
        replaceRun(updated);
        notifyRunListeners(updated, LifecycleEvent.RUN_FAILED);
    }

    public List<ExperimentRun> getRuns(UUID experimentId) {
        return Collections.unmodifiableList(
                new ArrayList<>(runs.getOrDefault(experimentId, List.of()))
        );
    }

    public Optional<ExperimentRun> getRun(UUID runId) {
        return Optional.ofNullable(findRun(runId));
    }

    public Map<ExperimentStatus, Long> getExperimentStats() {
        Map<ExperimentStatus, Long> stats = new EnumMap<>(ExperimentStatus.class);
        for (ExperimentStatus status : ExperimentStatus.values()) {
            stats.put(status, 0L);
        }
        experiments.values().forEach(e -> stats.merge(e.status(), 1L, Long::sum));
        return stats;
    }

    public Map<ExperimentRunStatus, Long> getRunStats() {
        Map<ExperimentRunStatus, Long> stats = new EnumMap<>(ExperimentRunStatus.class);
        for (ExperimentRunStatus status : ExperimentRunStatus.values()) {
            stats.put(status, 0L);
        }
        runs.values().stream()
                .flatMap(List::stream)
                .forEach(r -> stats.merge(r.status(), 1L, Long::sum));
        return stats;
    }

    public void subscribeToExperiments(Consumer<Experiment> listener) {
        experimentListeners.add(listener);
    }

    public void subscribeToRuns(Consumer<RunEvent> listener) {
        runListeners.add(listener);
    }

    private ExperimentRun findRun(UUID runId) {
        return runs.values().stream()
                .flatMap(List::stream)
                .filter(r -> r.runId().equals(runId))
                .findFirst()
                .orElse(null);
    }

    private void replaceRun(ExperimentRun updated) {
        runs.computeIfAbsent(updated.experimentId(), k -> new ArrayList<>())
                .replaceAll(r -> r.runId().equals(updated.runId()) ? updated : r);
    }

    private boolean allRunsComplete(UUID experimentId) {
        List<ExperimentRun> experimentRuns = runs.get(experimentId);
        if (experimentRuns == null) return true;
        return experimentRuns.stream().allMatch(r ->
                r.status() == ExperimentRunStatus.COMPLETED || r.status() == ExperimentRunStatus.FAILED
        );
    }

    private void notifyExperimentListeners(Experiment experiment, LifecycleEvent event) {
        for (Consumer<Experiment> listener : experimentListeners) {
            listener.accept(experiment);
        }
    }

    private void notifyRunListeners(ExperimentRun run, LifecycleEvent event) {
        RunEvent runEvent = new RunEvent(run, event);
        for (Consumer<RunEvent> listener : runListeners) {
            listener.accept(runEvent);
        }
    }

    public record RunEvent(ExperimentRun run, LifecycleEvent event) {}
}