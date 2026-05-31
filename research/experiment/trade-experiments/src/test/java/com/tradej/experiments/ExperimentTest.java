package com.tradej.experiments;

import com.tradej.pipeline.platform.PipelineType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ExperimentTest {

    @Test
    void createProducesDraftExperiment() {
        UUID pipelineDefId = UUID.randomUUID();

        Experiment exp = Experiment.create(
                "Test Experiment",
                "Testing hypothesis",
                PipelineType.SCANNER,
                pipelineDefId,
                "test-user"
        );

        assertNotNull(exp.experimentId());
        assertEquals("Test Experiment", exp.name());
        assertEquals("Testing hypothesis", exp.hypothesis());
        assertEquals(PipelineType.SCANNER, exp.pipelineType());
        assertEquals(pipelineDefId, exp.pipelineDefinitionId());
        assertEquals(ExperimentStatus.DRAFT, exp.status());
        assertEquals("test-user", exp.owner());
        assertNotNull(exp.createdAt());
        assertNotNull(exp.updatedAt());
        assertTrue(exp.tags().isEmpty());
    }

    @Test
    void withStatusCreatesNewInstance() {
        Experiment exp = Experiment.create(
                "Test", "Hypothesis", PipelineType.STRATEGY, UUID.randomUUID(), "user"
        );

        Experiment updated = exp.withStatus(ExperimentStatus.RUNNING);

        assertEquals(ExperimentStatus.RUNNING, updated.status());
        assertEquals(exp.experimentId(), updated.experimentId());
        assertEquals(exp.name(), updated.name());
        assertNotEquals(exp.updatedAt(), updated.updatedAt());
    }

    @Test
    void experimentRecordsAreImmutable() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Experiment exp = new Experiment(
                id, "name", "hypothesis", PipelineType.REPLAY, UUID.randomUUID(),
                ExperimentStatus.COMPLETED, "owner", now, now, Map.of("key", "value")
        );

        assertEquals(id, exp.experimentId());
        assertEquals("name", exp.name());
        assertEquals("hypothesis", exp.hypothesis());
        assertEquals(PipelineType.REPLAY, exp.pipelineType());
        assertEquals("value", exp.tags().get("key"));
    }
}