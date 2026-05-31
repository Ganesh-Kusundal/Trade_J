package com.tradej.pipeline.platform.model;

import com.tradej.pipeline.platform.PipelineType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PipelineExecutionUnitTest {

    private static PipelineExecution running() {
        return new PipelineExecution(
                UUID.randomUUID(), UUID.randomUUID(),
                new com.tradej.pipeline.platform.PipelineVersion(1, 0, 0),
                PipelineType.SCANNER,
                PipelineExecutionStatus.RUNNING,
                "operator-1",
                Instant.now(), Instant.now(), null, null, null,
                List.of(), Map.of(), Map.of()
        );
    }

    @Test
    void runningIsNotTerminal() {
        PipelineExecution exec = running();
        assertFalse(exec.isTerminal());
        assertTrue(exec.hasStarted());
        assertFalse(exec.hasFinished());
    }

    @Test
    void completedIsTerminalAndFinished() {
        PipelineExecution exec = running();
        PipelineExecution completed = new PipelineExecution(
                exec.executionId(), exec.pipelineId(), exec.pipelineVersion(),
                exec.pipelineType(), PipelineExecutionStatus.COMPLETED,
                exec.triggeredBy(), exec.triggeredAt(), exec.startedAt(),
                Instant.now(), 42L, null, List.of(), Map.of(), Map.of()
        );
        assertTrue(completed.isTerminal());
        assertTrue(completed.hasFinished());
    }

    @Test
    void rejectsNullExecutionId() {
        assertThrows(NullPointerException.class, () ->
                new PipelineExecution(null, UUID.randomUUID(),
                        new com.tradej.pipeline.platform.PipelineVersion(1, 0, 0),
                        PipelineType.SCANNER, PipelineExecutionStatus.QUEUED,
                        "x", Instant.now(), null, null, null, null,
                        List.of(), Map.of(), Map.of()));
    }
}
