package com.tradej.pipeline.platform.catalog;

import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.platform.PipelineDefinition;
import com.tradej.pipeline.platform.PipelineStatus;
import com.tradej.pipeline.platform.PipelineType;
import com.tradej.pipeline.platform.PipelineVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class InMemoryPipelineStoreTest {

    private InMemoryPipelineStore store = new InMemoryPipelineStore();

    private PipelineDefinition createDefinition(String name) {
        return new PipelineDefinition(
                UUID.randomUUID(),
                name,
                "test desc",
                PipelineType.SCANNER,
                new PipelineGraph("g", "G", 1, List.of(), List.of(), null),
                new PipelineVersion(1, 0, 0),
                PipelineStatus.DRAFT,
                Instant.now(),
                Instant.now(),
                Map.of()
        );
    }

    @Test
    void saveAndLoadDefinition() {
        PipelineDefinition def = createDefinition("test-pipeline");
        store.saveDefinition(def);

        Optional<PipelineDefinition> loaded = store.loadDefinition(def.id());

        assertTrue(loaded.isPresent());
        assertEquals(def.id(), loaded.get().id());
        assertEquals("test-pipeline", loaded.get().name());
    }

    @Test
    void loadNonExistentDefinition() {
        Optional<PipelineDefinition> loaded = store.loadDefinition(UUID.randomUUID());
        assertTrue(loaded.isEmpty());
    }

    @Test
    void listDefinitionsReturnsAll() {
        store.saveDefinition(createDefinition("pipeline-1"));
        store.saveDefinition(createDefinition("pipeline-2"));

        List<PipelineDefinition> all = store.listDefinitions();

        assertEquals(2, all.size());
    }

    @Test
    void updateDefinitionOverwrites() {
        PipelineDefinition def = createDefinition("original");
        store.saveDefinition(def);

        PipelineDefinition updated = new PipelineDefinition(
                def.id(), "updated", def.description(), def.type(), def.graph(),
                def.version(), PipelineStatus.PUBLISHED, def.createdAt(), Instant.now(), def.metadata()
        );
        store.saveDefinition(updated);

        Optional<PipelineDefinition> loaded = store.loadDefinition(def.id());
        assertTrue(loaded.isPresent());
        assertEquals("updated", loaded.get().name());
        assertEquals(PipelineStatus.PUBLISHED, loaded.get().status());
    }

    @Test
    void saveSnapshotAndLoad() {
        var snapshot = new com.tradej.pipeline.platform.PipelineSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new PipelineVersion(1, 0, 0),
                PipelineStatus.PUBLISHED,
                Instant.now(),
                Map.of("event", "test")
        );
        store.saveSnapshot(snapshot);

        Optional<com.tradej.pipeline.platform.PipelineSnapshot> loaded = store.loadSnapshot(snapshot.snapshotId());

        assertTrue(loaded.isPresent());
        assertEquals(snapshot.snapshotId(), loaded.get().snapshotId());
    }

    @Test
    void saveAndLoadExecution() {
        UUID pipelineId = UUID.randomUUID();
        var exec = new com.tradej.pipeline.platform.model.PipelineExecution(
                UUID.randomUUID(),
                pipelineId,
                new PipelineVersion(1, 0, 0),
                PipelineType.SCANNER,
                com.tradej.pipeline.platform.model.PipelineExecutionStatus.RUNNING,
                "test",
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                List.of(),
                Map.of(),
                Map.of()
        );
        store.saveExecution(exec);

        Optional<com.tradej.pipeline.platform.model.PipelineExecution> loaded = store.loadExecution(exec.executionId());

        assertTrue(loaded.isPresent());
        assertEquals(exec.executionId(), loaded.get().executionId());
    }

    @Test
    void listSnapshots() {
        store.saveSnapshot(new com.tradej.pipeline.platform.PipelineSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), new PipelineVersion(1, 0, 0),
                PipelineStatus.PUBLISHED, Instant.now(), Map.of()
        ));

        var snapshots = store.listSnapshots();
        assertEquals(1, snapshots.size());
    }
}