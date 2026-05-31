package com.tradej.persistence.pipeline;

import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DuckDbPipelineGraphStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsVersionedGraphs() throws Exception {
        Path dbPath = tempDir.resolve("pipeline-graphs.duckdb");
        PipelineGraph v1 = sampleGraph(1);
        PipelineGraph v2 = sampleGraph(2);

        try (DuckDbPipelineGraphStore store = new DuckDbPipelineGraphStore(dbPath)) {
            store.save(v1);
            store.save(v2);

            PipelineGraph latest = store.loadLatest("hotpath-default").orElseThrow();
            assertEquals(2, latest.version());

            PipelineGraph loadedV1 = store.loadVersion("hotpath-default", 1).orElseThrow();
            assertEquals(1, loadedV1.version());

            List<DuckDbPipelineGraphStore.PipelineGraphVersion> versions = store.listVersions("hotpath-default");
            assertEquals(2, versions.size());
            assertTrue(versions.stream().anyMatch(v -> v.version() == 1));
            assertTrue(versions.stream().anyMatch(v -> v.version() == 2));
        }
    }

    private static PipelineGraph sampleGraph(int version) {
        return new PipelineGraph(
                "hotpath-default",
                "Test Pipeline",
                version,
                List.of(new PipelineNodeDef("risk-1", PipelineNodeTypes.RISK, "Risk", Map.of())),
                List.of()
        );
    }
}
