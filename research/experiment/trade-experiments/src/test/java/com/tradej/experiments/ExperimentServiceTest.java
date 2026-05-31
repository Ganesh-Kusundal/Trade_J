package com.tradej.experiments;

import com.tradej.pipeline.platform.PipelineType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ExperimentServiceTest {

    @Test
    void createExperiment() {
        ExperimentService service = new ExperimentService();

        Experiment experiment = service.create(
                "Test Experiment",
                "Testing hypothesis",
                PipelineType.REPLAY,
                UUID.randomUUID(),
                "tester"
        );

        assertNotNull(experiment.experimentId());
        assertEquals("Test Experiment", experiment.name());
        assertEquals("Testing hypothesis", experiment.hypothesis());
        assertEquals(PipelineType.REPLAY, experiment.pipelineType());
        assertEquals(ExperimentStatus.DRAFT, experiment.status());
        assertEquals("tester", experiment.owner());
    }

    @Test
    void getExperiment() {
        ExperimentService service = new ExperimentService();

        Experiment created = service.create(
                "Test", "Hypothesis", PipelineType.SCANNER, UUID.randomUUID(), "owner"
        );

        Experiment found = service.get(created.experimentId()).orElseThrow();

        assertEquals(created.experimentId(), found.experimentId());
    }

    @Test
    void getNonExistentExperiment() {
        ExperimentService service = new ExperimentService();

        assertTrue(service.get(UUID.randomUUID()).isEmpty());
    }

    @Test
    void updateExperimentStatus() {
        ExperimentService service = new ExperimentService();

        Experiment created = service.create(
                "Test", "Hypothesis", PipelineType.SCANNER, UUID.randomUUID(), "owner"
        );

        Experiment updated = service.updateStatus(created.experimentId(), ExperimentStatus.RUNNING);

        assertEquals(ExperimentStatus.RUNNING, updated.status());
    }

    @Test
    void startRun() {
        ExperimentService service = new ExperimentService();

        Experiment experiment = service.create(
                "Test", "Hypothesis", PipelineType.REPLAY, UUID.randomUUID(), "owner"
        );

        ExperimentRun run = service.startRun(experiment.experimentId(), Map.of("param", "value"));

        assertNotNull(run.runId());
        assertEquals(experiment.experimentId(), run.experimentId());
        assertEquals(ExperimentRunStatus.RUNNING, run.status());
        assertEquals(1, run.runNumber());
    }

    @Test
    void completeRun() {
        ExperimentService service = new ExperimentService();

        Experiment experiment = service.create(
                "Test", "Hypothesis", PipelineType.REPLAY, UUID.randomUUID(), "owner"
        );
        ExperimentRun run = service.startRun(experiment.experimentId(), Map.of());

        service.completeRun(run.runId(), Map.of("sharpe", 1.5));

        ExperimentRun completed = service.getRun(run.runId()).orElseThrow();
        assertEquals(ExperimentRunStatus.COMPLETED, completed.status());
        assertEquals(1.5, completed.metrics().get("sharpe"));
        assertNotNull(completed.finishedAt());
    }

    @Test
    void failRun() {
        ExperimentService service = new ExperimentService();

        Experiment experiment = service.create(
                "Test", "Hypothesis", PipelineType.REPLAY, UUID.randomUUID(), "owner"
        );
        ExperimentRun run = service.startRun(experiment.experimentId(), Map.of());

        service.failRun(run.runId(), "Test error");

        ExperimentRun failed = service.getRun(run.runId()).orElseThrow();
        assertEquals(ExperimentRunStatus.FAILED, failed.status());
        assertEquals("Test error", failed.metrics().get("error"));
    }

    @Test
    void getAllExperiments() {
        ExperimentService service = new ExperimentService();

        service.create("Exp1", "H1", PipelineType.SCANNER, UUID.randomUUID(), "owner");
        service.create("Exp2", "H2", PipelineType.STRATEGY, UUID.randomUUID(), "owner");

        List<Experiment> all = service.getAll();

        assertEquals(2, all.size());
    }

    @Test
    void getExperimentsByStatus() {
        ExperimentService service = new ExperimentService();

        Experiment exp1 = service.create("Exp1", "H1", PipelineType.SCANNER, UUID.randomUUID(), "owner");
        service.create("Exp2", "H2", PipelineType.STRATEGY, UUID.randomUUID(), "owner");
        service.updateStatus(exp1.experimentId(), ExperimentStatus.RUNNING);

        List<Experiment> running = service.getByStatus(ExperimentStatus.RUNNING);

        assertEquals(1, running.size());
        assertEquals("Exp1", running.get(0).name());
    }

    @Test
    void getRunsForExperiment() {
        ExperimentService service = new ExperimentService();

        Experiment experiment = service.create(
                "Test", "Hypothesis", PipelineType.REPLAY, UUID.randomUUID(), "owner"
        );
        service.startRun(experiment.experimentId(), Map.of("p1", 1));
        service.startRun(experiment.experimentId(), Map.of("p2", 2));

        List<ExperimentRun> runs = service.getRuns(experiment.experimentId());

        assertEquals(2, runs.size());
    }

    @Test
    void deleteExperiment() {
        ExperimentService service = new ExperimentService();

        Experiment experiment = service.create(
                "Test", "Hypothesis", PipelineType.REPLAY, UUID.randomUUID(), "owner"
        );

        service.delete(experiment.experimentId());

        assertTrue(service.get(experiment.experimentId()).isEmpty());
    }

    @Test
    void getExperimentStats() {
        ExperimentService service = new ExperimentService();

        Experiment exp1 = service.create("Exp1", "H1", PipelineType.SCANNER, UUID.randomUUID(), "owner");
        service.create("Exp2", "H2", PipelineType.STRATEGY, UUID.randomUUID(), "owner");
        service.updateStatus(exp1.experimentId(), ExperimentStatus.COMPLETED);

        var stats = service.getExperimentStats();

        assertEquals(1L, stats.get(ExperimentStatus.DRAFT));
        assertEquals(1L, stats.get(ExperimentStatus.COMPLETED));
    }

    @Test
    void subscribeToExperiments() {
        ExperimentService service = new ExperimentService();
        var captured = new java.util.concurrent.atomic.AtomicReference<Experiment>();

        service.subscribeToExperiments(captured::set);

        Experiment created = service.create(
                "Test", "Hypothesis", PipelineType.REPLAY, UUID.randomUUID(), "owner"
        );

        assertEquals(created.experimentId(), captured.get().experimentId());
    }
}