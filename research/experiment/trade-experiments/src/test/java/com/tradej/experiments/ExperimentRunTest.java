package com.tradej.experiments;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ExperimentRunTest {

    @Test
    void experimentRunRecordConstruction() {
        UUID runId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant finish = Instant.now().plusSeconds(60);

        ExperimentRun run = new ExperimentRun(
                runId,
                experimentId,
                1,
                null,
                ExperimentRunStatus.COMPLETED,
                Map.of("param1", "value1"),
                Map.of("accuracy", 0.95),
                start,
                finish
        );

        assertEquals(runId, run.runId());
        assertEquals(experimentId, run.experimentId());
        assertEquals(1, run.runNumber());
        assertNull(run.execution());
        assertEquals(ExperimentRunStatus.COMPLETED, run.status());
        assertEquals("value1", run.parameters().get("param1"));
        assertEquals(0.95, run.metrics().get("accuracy"));
        assertEquals(start, run.startedAt());
        assertEquals(finish, run.finishedAt());
    }

    @Test
    void experimentRunStatusTransitions() {
        assertNotNull(ExperimentRunStatus.QUEUED);
        assertNotNull(ExperimentRunStatus.RUNNING);
        assertNotNull(ExperimentRunStatus.COMPLETED);
        assertNotNull(ExperimentRunStatus.FAILED);
        assertNotNull(ExperimentRunStatus.CANCELLED);
        assertEquals(5, ExperimentRunStatus.values().length);
    }
}