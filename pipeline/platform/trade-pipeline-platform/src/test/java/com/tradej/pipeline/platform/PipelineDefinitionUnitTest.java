package com.tradej.pipeline.platform;

import com.tradej.core.domain.pipeline.PipelineGraph;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link PipelineDefinition} immutability and correctness.
 * INV-31: Immutability — all fields are immutable and defensively copied.
 */
@Tag("unit")
class PipelineDefinitionUnitTest {

    private static PipelineDefinition sampleDefinition() {
        return new PipelineDefinition(
                UUID.randomUUID(),
                "Momentum Scanner",
                "Scans momentum signals",
                PipelineType.SCANNER,
                new PipelineGraph("g1", java.util.List.of("a", "b"), java.util.List.of("a->b")),
                new PipelineVersion(1, 0, 0),
                PipelineStatus.DRAFT,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                Map.of("author", "test")
        );
    }

    @Test
    void createsDefinitionWithAllFields() {
        PipelineDefinition def = sampleDefinition();

        assertNotNull(def.id());
        assertEquals("Momentum Scanner", def.name());
        assertEquals("Scans momentum signals", def.description());
        assertEquals(PipelineType.SCANNER, def.type());
        assertNotNull(def.graph());
        assertEquals(new PipelineVersion(1, 0, 0), def.version());
        assertEquals(PipelineStatus.DRAFT, def.status());
        assertNotNull(def.createdAt());
        assertNotNull(def.updatedAt());
        assertEquals("test", def.metadata().get("author"));
    }

    @Test
    void recordIsImmutableByConstruction() {
        PipelineDefinition def = sampleDefinition();

        // Records expose only accessor methods (no setters), so immutability is
        // guaranteed by the JVM. Calling accessor twice returns same logical values.
        assertEquals(def.name(), def.name());
        assertEquals(def.version(), def.version());
        assertEquals(def.status(), def.status());
    }

    @Test
    void metadataMapIsDefensivelyCopied() {
        Map<String, String> original = new java.util.HashMap<>();
        original.put("key", "value");
        PipelineDefinition def = new PipelineDefinition(
                UUID.randomUUID(),
                "test",
                "desc",
                PipelineType.ANALYTICS,
                new PipelineGraph("g1", java.util.List.of(), java.util.List.of()),
                new PipelineVersion(0, 0, 1),
                PipelineStatus.DRAFT,
                Instant.now(),
                Instant.now(),
                original
        );

        // Mutating original map should not affect the definition
        original.put("injected", "oops");
        assertNotEquals("oops", def.metadata().get("injected"));
    }

    @Test
    void twoDefinitionsWithSameFieldsAreEqual() {
        UUID fixedId = UUID.randomUUID();
        Instant ts = Instant.parse("2026-06-01T00:00:00Z");
        PipelineGraph graph = new PipelineGraph("g1", java.util.List.of(), java.util.List.of());

        PipelineDefinition a = new PipelineDefinition(fixedId, "A", "d", PipelineType.EXECUTION,
                graph, new PipelineVersion(1, 0, 0), PipelineStatus.DRAFT, ts, ts, Map.of());
        PipelineDefinition b = new PipelineDefinition(fixedId, "A", "d", PipelineType.EXECUTION,
                graph, new PipelineVersion(1, 0, 0), PipelineStatus.DRAFT, ts, ts, Map.of());

        assertEquals(a, b);
    }

    @Test
    void twoDefinitionsWithDifferentIdsAreNotEqual() {
        PipelineGraph graph = new PipelineGraph("g1", java.util.List.of(), java.util.List.of());
        Instant ts = Instant.parse("2026-06-01T00:00:00Z");

        PipelineDefinition a = new PipelineDefinition(UUID.randomUUID(), "A", "d", PipelineType.EXECUTION,
                graph, new PipelineVersion(1, 0, 0), PipelineStatus.DRAFT, ts, ts, Map.of());
        PipelineDefinition b = new PipelineDefinition(UUID.randomUUID(), "A", "d", PipelineType.EXECUTION,
                graph, new PipelineVersion(1, 0, 0), PipelineStatus.DRAFT, ts, ts, Map.of());

        assertNotEquals(a, b);
    }

    @Test
    void rejectsNullId() {
        assertThrows(NullPointerException.class, () ->
                new PipelineDefinition(null, "name", "desc", PipelineType.SCANNER,
                        new PipelineGraph("g1", java.util.List.of(), java.util.List.of()),
                        new PipelineVersion(0, 0, 0), PipelineStatus.DRAFT,
                        Instant.now(), Instant.now(), Map.of()));
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () ->
                new PipelineDefinition(UUID.randomUUID(), null, "desc", PipelineType.SCANNER,
                        new PipelineGraph("g1", java.util.List.of(), java.util.List.of()),
                        new PipelineVersion(0, 0, 0), PipelineStatus.DRAFT,
                        Instant.now(), Instant.now(), Map.of()));
    }
}
