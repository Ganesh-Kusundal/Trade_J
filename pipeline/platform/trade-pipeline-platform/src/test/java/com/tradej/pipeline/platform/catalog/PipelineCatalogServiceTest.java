package com.tradej.pipeline.platform.catalog;

import com.tradej.pipeline.platform.PipelineDefinition;
import com.tradej.pipeline.platform.PipelineSnapshot;
import com.tradej.pipeline.platform.PipelineStatus;
import com.tradej.pipeline.platform.PipelineStatus;
import com.tradej.pipeline.platform.PipelineType;
import com.tradej.pipeline.platform.model.PipelineExecution;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PipelineCatalogServiceTest {

    private static com.tradej.pipeline.graph.PipelineGraph graph() {
        return new com.tradej.pipeline.graph.PipelineGraph(
                "g", "graph", 1,
                List.of(new com.tradej.pipeline.graph.PipelineNodeDef("n1", "type", "N1", Map.of())),
                List.of(),
                com.tradej.pipeline.graph.PipelineExecutionMode.HOT_PATH);
    }

    private static PipelineCatalogService catalog() {
        return new PipelineCatalogService(new InMemoryPipelineStore());
    }

    @Test
    void createDefinitionDefaultsVersionTo001() {
        PipelineDefinition def = catalog().createDefinition(
                "name", "desc", PipelineType.SCANNER, graph(), null, Map.of());
        assertEquals(new com.tradej.pipeline.platform.PipelineVersion(0, 0, 1), def.version());
        assertEquals(com.tradej.pipeline.platform.PipelineStatus.DRAFT, def.status());
    }

    @Test
    void publishTransitionsToPublishedAndSnapshots() {
        PipelineCatalogService svc = catalog();
        PipelineDefinition def = svc.createDefinition(
                "n", "d", PipelineType.STRATEGY, graph(), null, Map.of());
        PipelineSnapshot snap = svc.publish(def.id(), "admin");
        assertEquals(PipelineStatus.PUBLISHED, snap.status());
        assertFalse(svc.snapshotsFor(def.id()).isEmpty());
    }

    @Test
    void publishNonDraftThrows() {
        PipelineCatalogService svc = catalog();
        PipelineDefinition def = svc.createDefinition(
                "n", "d", PipelineType.REPLAY, graph(), null, Map.of());
        svc.publish(def.id(), "x"); // transitions to PUBLISHED
        assertThrows(IllegalStateException.class,
                () -> svc.publish(def.id(), "x")); // second publish must fail
    }

    @Test
    void startExecutionLinksDefinitionAndExecution() {
        PipelineCatalogService svc = catalog();
        PipelineDefinition def = svc.createDefinition(
                "n", "d", PipelineType.OPTIMIZATION, graph(), null, Map.of());
        PipelineExecution exec = svc.startExecution(def.id(), "scheduler-1");
        assertEquals(def.id(), exec.pipelineId());
        assertEquals(com.tradej.pipeline.platform.model.PipelineExecutionStatus.RUNNING,
                exec.status());
        assertEquals("scheduler-1", exec.triggeredBy());
    }

    @Test
    void executionsForDefinitionReturnsLinkedRuns() {
        PipelineCatalogService svc = catalog();
        PipelineDefinition def = svc.createDefinition(
                "n", "d", PipelineType.EXECUTION, graph(), null, Map.of());
        svc.startExecution(def.id(), "a");
        svc.startExecution(def.id(), "b");
        List<PipelineExecution> runs = svc.executionsForDefinition(def.id());
        assertEquals(2, runs.size());
    }
}
