package com.tradej.pipeline.platform.catalog;

import com.tradej.pipeline.platform.PipelineDefinition;
import com.tradej.pipeline.platform.PipelineSnapshot;
import com.tradej.pipeline.platform.PipelineStatus;
import com.tradej.pipeline.platform.PipelineVersion;
import com.tradej.pipeline.platform.PipelineType;
import com.tradej.pipeline.platform.model.PipelineExecution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory catalog for pipeline definition lifecycle operations.
 *
 * <p>This is the single entrypoint for creating, cloning, versioning,
 * publishing, archiving, and rolling back pipeline definitions. Persistence
 * is delegated to a backing store interface; the default implementation keeps
 * everything in memory and is suitable for testing and as a migration shim
 * until the DuckDB-backed catalog is wired in.
 *
 * <p>Key invariants:
 * <ul>
 *   <li>A definition is immutable once published (stored as a {@link PipelineSnapshot}).</li>
 *   <li>Cloning resets status to DRAFT and assigns a new definition id.</li>
 *   <li>Version bumps follow semver rules exposed by {@link PipelineVersion}.</li>
 *   <li>Rollback creates a new definition referencing the snapshot's graph and version.</li>
 * </ul>
 */
public class PipelineCatalogService {

    private final PipelineStore store;

    public PipelineCatalogService(PipelineStore store) {
        this.store = store;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public PipelineDefinition createDefinition(String name,
                                                String description,
                                                PipelineType type,
                                                com.tradej.pipeline.graph.PipelineGraph graph,
                                                PipelineVersion version,
                                                Map<String, String> metadata) {
        PipelineDefinition def = new PipelineDefinition(
                UUID.randomUUID(),
                name,
                description,
                type,
                graph,
                version == null ? new PipelineVersion(0, 0, 1) : version,
                PipelineStatus.DRAFT,
                Instant.now(),
                Instant.now(),
                metadata == null ? Map.of() : Map.copyOf(metadata)
        );
        store.saveDefinition(def);
        return def;
    }

    public Optional<PipelineDefinition> findDefinition(UUID definitionId) {
        return store.loadDefinition(definitionId);
    }

    public List<PipelineDefinition> listDefinitions() {
        return store.listDefinitions();
    }

    public List<PipelineDefinition> listByType(PipelineType type) {
        return store.listDefinitions().stream()
                .filter(d -> d.type() == type)
                .toList();
    }

    // ── Lifecycle transitions ────────────────────────────────────────────────

    /**
     * Publish a DRAFT definition. Once published, the definition graph is
     * captured as an immutable snapshot and cannot be mutated in place.
     */
    public PipelineSnapshot publish(UUID definitionId, String publishedBy) {
        PipelineDefinition current = requireDefinition(definitionId);
        if (current.status() != PipelineStatus.DRAFT) {
            throw new IllegalStateException("Cannot publish definition " + definitionId
                    + " in status " + current.status());
        }
        PipelineDefinition published = new PipelineDefinition(
                current.id(),
                current.name(),
                current.description(),
                current.type(),
                current.graph(),
                current.version(),
                PipelineStatus.PUBLISHED,
                current.createdAt(),
                Instant.now(),
                current.metadata()
        );
        store.saveDefinition(published);
        return captureSnapshot(published, "published by " + publishedBy);
    }

    /**
     * Archive a published definition.
     */
    public PipelineDefinition archive(UUID definitionId) {
        PipelineDefinition current = requireDefinition(definitionId);
        if (current.status() == PipelineStatus.ARCHIVED) {
            return current;
        }
        PipelineDefinition archived = transition(current, PipelineStatus.ARCHIVED);
        store.saveDefinition(archived);
        store.saveSnapshot(new PipelineSnapshot(
                UUID.randomUUID(),
                archived.id(),
                archived.version(),
                archived.status(),
                Instant.now(),
                Map.of("event", "archived")
        ));
        return archived;
    }

    /**
     * Roll back to a previously captured snapshot by creating a new draft
     * definition whose graph and version match the snapshot.
     */
    public PipelineDefinition rollbackToSnapshot(UUID snapshotId, String rolledBackBy) {
        PipelineSnapshot snapshot = store.loadSnapshot(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Snapshot not found: " + snapshotId));
        PipelineDefinition original = store.loadDefinition(snapshot.pipelineId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Original definition missing for pipeline " + snapshot.pipelineId()));

        PipelineDefinition rolledBack = new PipelineDefinition(
                UUID.randomUUID(),
                original.name() + " (rollback " + snapshot.version() + ")",
                original.description(),
                original.type(),
                original.graph(),
                snapshot.version(),
                PipelineStatus.DRAFT,
                Instant.now(),
                Instant.now(),
                Map.copyOf(original.metadata())
        );
        store.saveDefinition(rolledBack);
        store.saveSnapshot(new PipelineSnapshot(
                UUID.randomUUID(),
                rolledBack.id(),
                rolledBack.version(),
                rolledBack.status(),
                Instant.now(),
                Map.of("rolledBackFromSnapshotId", snapshotId.toString(),
                        "rolledBackBy", rolledBackBy)
        ));
        return rolledBack;
    }

    // ── Versioning helpers ───────────────────────────────────────────────────

    public PipelineDefinition bumpMajor(UUID definitionId) {
        return bumpVersion(definitionId, PipelineVersion::nextMajor);
    }

    public PipelineDefinition bumpMinor(UUID definitionId) {
        return bumpVersion(definitionId, PipelineVersion::nextMinor);
    }

    public PipelineDefinition bumpPatch(UUID definitionId) {
        return bumpVersion(definitionId, PipelineVersion::nextPatch);
    }

    private PipelineDefinition bumpVersion(UUID definitionId,
                                           java.util.function.Function<PipelineVersion, PipelineVersion> bump) {
        PipelineDefinition current = requireDefinition(definitionId);
        if (current.status() != PipelineStatus.DRAFT) {
            throw new IllegalStateException("Cannot bump version for definition " + definitionId
                    + " in status " + current.status() + "; clone to a draft first.");
        }
        PipelineDefinition updated = new PipelineDefinition(
                current.id(),
                current.name(),
                current.description(),
                current.type(),
                current.graph(),
                bump.apply(current.version()),
                current.status(),
                current.createdAt(),
                Instant.now(),
                current.metadata()
        );
        store.saveDefinition(updated);
        return updated;
    }

    // ── Snapshot queries ────────────────────────────────────────────────────

    public List<PipelineSnapshot> snapshotsFor(UUID definitionId) {
        return store.listSnapshots().stream()
                .filter(s -> s.pipelineId().equals(definitionId))
                .toList();
    }

    // ── Execution tracking ──────────────────────────────────────────────────

    public PipelineExecution startExecution(UUID definitionId, String triggeredBy) {
        PipelineDefinition def = requireDefinition(definitionId);
        PipelineExecution exec = new PipelineExecution(
                UUID.randomUUID(),
                def.id(),
                def.version(),
                def.type(),
                com.tradej.pipeline.platform.model.PipelineExecutionStatus.RUNNING,
                triggeredBy,
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
        return exec;
    }

    public Optional<PipelineExecution> findExecution(UUID executionId) {
        return store.loadExecution(executionId);
    }

    public List<PipelineExecution> executionsForDefinition(UUID definitionId) {
        return store.listExecutions().stream()
                .filter(e -> e.pipelineId().equals(definitionId))
                .toList();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private PipelineDefinition requireDefinition(UUID definitionId) {
        return store.loadDefinition(definitionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pipeline definition not found: " + definitionId));
    }

    private PipelineDefinition transition(PipelineDefinition current, PipelineStatus newStatus) {
        return new PipelineDefinition(
                current.id(),
                current.name(),
                current.description(),
                current.type(),
                current.graph(),
                current.version(),
                newStatus,
                current.createdAt(),
                Instant.now(),
                current.metadata()
        );
    }

    private PipelineSnapshot captureSnapshot(PipelineDefinition published, String trigger) {
        PipelineSnapshot snapshot = new PipelineSnapshot(
                UUID.randomUUID(),
                published.id(),
                published.version(),
                published.status(),
                Instant.now(),
                Map.of("trigger", trigger)
        );
        store.saveSnapshot(snapshot);
        return snapshot;
    }
}
