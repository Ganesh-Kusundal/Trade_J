package com.tradej.pipeline.platform;

import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PipelineTemplateTest {

    @Test
    void instantiateCreatesDraftDefinition() {
        PipelineGraph graph = new PipelineGraph(
                "graph-1",
                "Test Graph",
                1,
                List.of(new PipelineNodeDef("node-1", "candle", "Candle", Map.of())),
                List.of(),
                PipelineExecutionMode.HOT_PATH
        );

        PipelineTemplate template = new PipelineTemplate(
                UUID.randomUUID(),
                "Test Template",
                "A test template",
                PipelineType.STRATEGY,
                graph,
                new PipelineVersion(1, 0, 0),
                Map.of("env", "test")
        );

        PipelineDefinition def = template.instantiate();

        assertNotNull(def.id());
        assertEquals("Test Template", def.name());
        assertEquals("A test template", def.description());
        assertEquals(PipelineType.STRATEGY, def.type());
        assertEquals(graph, def.graph());
        assertEquals(new PipelineVersion(1, 0, 0), def.version());
        assertEquals(PipelineStatus.DRAFT, def.status());
        assertNotNull(def.createdAt());
        assertNotNull(def.updatedAt());
        assertEquals("test", def.metadata().get("env"));
    }

    @Test
    void templateRequiresNonNullFields() {
        UUID id = UUID.randomUUID();
        PipelineGraph graph = new PipelineGraph("g", "G", 1, List.of(), List.of(), null);

        assertThrows(NullPointerException.class, () ->
                new PipelineTemplate(null, "name", "desc", PipelineType.SCANNER, graph, new PipelineVersion(1, 0, 0), Map.of())
        );

        assertThrows(NullPointerException.class, () ->
                new PipelineTemplate(id, null, "desc", PipelineType.SCANNER, graph, new PipelineVersion(1, 0, 0), Map.of())
        );
    }

    @Test
    void templateMetadataIsDefensivelyCopied() {
        UUID id = UUID.randomUUID();
        PipelineGraph graph = new PipelineGraph("g", "G", 1, List.of(), List.of(), null);

        java.util.Map<String, String> meta = new java.util.HashMap<>();
        meta.put("key", "value");

        PipelineTemplate template = new PipelineTemplate(
                id, "name", "desc", PipelineType.REPLAY, graph, new PipelineVersion(1, 0, 0), meta
        );

        meta.put("key", "modified");

        assertEquals("value", template.defaults().get("key"));
    }
}