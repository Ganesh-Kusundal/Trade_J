package com.tradej.pipeline.platform;

import com.tradej.core.domain.pipeline.PipelineGraph;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Component test wiring {@link PipelineGraph} (from trade-core) with {@link PipelineDefinition}.
 * Verifies cross-module integration.
 */
@Tag("component")
class PipelineGraphComponentTest {

    @Test
    void wiresPipelineGraphIntoDefinitionSuccessfully() {
        PipelineGraph graph = new PipelineGraph(
                "scanner-graph-1",
                List.of("fetch-ticks", "aggregate-candles", "detect-signals"),
                List.of("fetch-ticks->aggregate-candles", "aggregate-candles->detect-signals")
        );

        PipelineDefinition definition = new PipelineDefinition(
                UUID.randomUUID(),
                "Momentum Scanner Pipeline",
                "End-to-end momentum detection pipeline",
                PipelineType.SCANNER,
                graph,
                new PipelineVersion(1, 0, 0),
                PipelineStatus.DRAFT,
                Instant.now(),
                Instant.now(),
                Map.of("team", "quant")
        );

        assertNotNull(definition.graph());
        assertEquals("scanner-graph-1", definition.graph().graphId());
        assertEquals(3, definition.graph().nodeIds().size());
        assertEquals(2, definition.graph().edges().size());
        assertEquals(PipelineType.SCANNER, definition.type());
    }

    @Test
    void graphNodesAndEdgesAreReadOnly() {
        PipelineGraph graph = new PipelineGraph(
                "g1",
                List.of("node-a", "node-b"),
                List.of("a->b")
        );

        // Defensive copies prevent external mutation
        List<String> nodes = graph.nodeIds();
        List<String> edges = graph.edges();

        assertTrue(nodes.contains("node-a"));
        assertTrue(edges.contains("a->b"));

        // The lists are unmodifiable
        try {
            nodes.add("node-c");
            throw new AssertionError("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    void fullPipelineLifecycleWithGraph() {
        PipelineGraph graph = new PipelineGraph(
                "replay-graph",
                List.of("load-data", "replay-events", "compute-metrics"),
                List.of("load-data->replay-events", "replay-events->compute-metrics")
        );

        PipelineDefinition def = new PipelineDefinition(
                UUID.randomUUID(),
                "Backtest Replay",
                "Historical replay for strategy validation",
                PipelineType.REPLAY,
                graph,
                new PipelineVersion(1, 0, 0),
                PipelineStatus.DRAFT,
                Instant.now(),
                Instant.now(),
                Map.of()
        );

        // Simulate version bump + publish
        PipelineDefinition published = new PipelineDefinition(
                def.id(),
                def.name(),
                def.description(),
                def.type(),
                def.graph(),
                def.version().nextMajor(),
                PipelineStatus.PUBLISHED,
                def.createdAt(),
                Instant.now(),
                def.metadata()
        );

        assertEquals(new PipelineVersion(2, 0, 0), published.version());
        assertEquals(PipelineStatus.PUBLISHED, published.status());
        assertEquals(def.id(), published.id());

        // Simulate snapshot
        PipelineSnapshot snapshot = new PipelineSnapshot(
                UUID.randomUUID(),
                published.id(),
                published.version(),
                published.status(),
                Instant.now(),
                Map.of("phase", "published")
        );

        assertEquals(published.version(), snapshot.version());
        assertEquals(published.id(), snapshot.pipelineId());
    }

    @Test
    void emptyGraphIsValid() {
        PipelineGraph emptyGraph = new PipelineGraph("empty", List.of(), List.of());

        PipelineDefinition def = new PipelineDefinition(
                UUID.randomUUID(),
                "Empty Pipeline",
                "A pipeline with no nodes yet",
                PipelineType.OPTIMIZATION,
                emptyGraph,
                new PipelineVersion(0, 0, 1),
                PipelineStatus.DRAFT,
                Instant.now(),
                Instant.now(),
                Map.of()
        );

        assertNotNull(def.graph());
        assertTrue(def.graph().nodeIds().isEmpty());
        assertTrue(def.graph().edges().isEmpty());
    }
}
