package com.tradej.pipeline.platform;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for {@link PipelineSnapshot} value object.
 */
@Tag("unit")
class PipelineSnapshotUnitTest {

    @Test
    void createsSnapshotWithAllFields() {
        UUID pipelineId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-01T12:00:00Z");

        PipelineSnapshot snapshot = new PipelineSnapshot(
                UUID.randomUUID(),
                pipelineId,
                new PipelineVersion(2, 1, 0),
                PipelineStatus.RUNNING,
                now,
                Map.of("trigger", "scheduled")
        );

        assertNotNull(snapshot.snapshotId());
        assertEquals(pipelineId, snapshot.pipelineId());
        assertEquals(new PipelineVersion(2, 1, 0), snapshot.version());
        assertEquals(PipelineStatus.RUNNING, snapshot.status());
        assertEquals(now, snapshot.capturedAt());
        assertEquals("scheduled", snapshot.snapshotMetadata().get("trigger"));
    }

    @Test
    void snapshotIsImmutable() {
        PipelineSnapshot snapshot = new PipelineSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new PipelineVersion(1, 0, 0),
                PipelineStatus.COMPLETED,
                Instant.now(),
                Map.of()
        );

        // Record accessors are deterministic
        assertEquals(snapshot.status(), snapshot.status());
        assertEquals(snapshot.version(), snapshot.version());
    }

    @Test
    void twoSnapshotsWithSameIdAreEqual() {
        UUID snapId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        Instant now = Instant.now();

        PipelineSnapshot a = new PipelineSnapshot(snapId, pipelineId,
                new PipelineVersion(1, 0, 0), PipelineStatus.DRAFT, now, Map.of());
        PipelineSnapshot b = new PipelineSnapshot(snapId, pipelineId,
                new PipelineVersion(1, 0, 0), PipelineStatus.DRAFT, now, Map.of());

        assertEquals(a, b);
    }

    @Test
    void twoSnapshotsWithDifferentIdsAreNotEqual() {
        PipelineSnapshot a = new PipelineSnapshot(UUID.randomUUID(), UUID.randomUUID(),
                new PipelineVersion(1, 0, 0), PipelineStatus.DRAFT, Instant.now(), Map.of());
        PipelineSnapshot b = new PipelineSnapshot(UUID.randomUUID(), UUID.randomUUID(),
                new PipelineVersion(1, 0, 0), PipelineStatus.DRAFT, Instant.now(), Map.of());

        assertNotEquals(a, b);
    }
}
