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
class PipelineTemplateServiceTest {

    private PipelineGraph createTestGraph() {
        return new PipelineGraph(
                "test-graph",
                "Test Graph",
                1,
                List.of(new PipelineNodeDef("node-1", "candle", "Candle", Map.of())),
                List.of(),
                PipelineExecutionMode.HOT_PATH
        );
    }

    @Test
    void createTemplate() {
        PipelineTemplateService service = new PipelineTemplateService();
        PipelineGraph graph = createTestGraph();

        PipelineTemplate template = service.createTemplate(
                "Scanner Template",
                "Template for scanner pipelines",
                PipelineType.SCANNER,
                graph,
                new PipelineVersion(1, 0, 0),
                Map.of("env", "test")
        );

        assertNotNull(template.templateId());
        assertEquals("Scanner Template", template.name());
        assertEquals(PipelineType.SCANNER, template.type());
        assertEquals(graph, template.graph());
        assertEquals("test", template.defaults().get("env"));
    }

    @Test
    void getTemplate() {
        PipelineTemplateService service = new PipelineTemplateService();
        PipelineGraph graph = createTestGraph();

        PipelineTemplate created = service.createTemplate(
                "Test", "Desc", PipelineType.STRATEGY, graph,
                new PipelineVersion(1, 0, 0), Map.of()
        );

        PipelineTemplate found = service.getTemplate(created.templateId()).orElseThrow();

        assertEquals(created.templateId(), found.templateId());
    }

    @Test
    void getNonExistentTemplate() {
        PipelineTemplateService service = new PipelineTemplateService();

        assertTrue(service.getTemplate(UUID.randomUUID()).isEmpty());
    }

    @Test
    void getTemplatesByType() {
        PipelineTemplateService service = new PipelineTemplateService();
        PipelineGraph graph = createTestGraph();

        service.createTemplate("S1", "D", PipelineType.SCANNER, graph, new PipelineVersion(1, 0, 0), Map.of());
        service.createTemplate("S2", "D", PipelineType.SCANNER, graph, new PipelineVersion(1, 0, 0), Map.of());
        service.createTemplate("S3", "D", PipelineType.STRATEGY, graph, new PipelineVersion(1, 0, 0), Map.of());

        List<PipelineTemplate> scanners = service.getTemplatesByType(PipelineType.SCANNER);

        assertEquals(2, scanners.size());
    }

    @Test
    void updateTemplate() {
        PipelineTemplateService service = new PipelineTemplateService();
        PipelineGraph graph = createTestGraph();

        PipelineTemplate created = service.createTemplate(
                "Original", "Original desc", PipelineType.SCANNER, graph,
                new PipelineVersion(1, 0, 0), Map.of("key", "value")
        );

        PipelineGraph newGraph = new PipelineGraph(
                "new-graph", "New Graph", 1, List.of(), List.of(), PipelineExecutionMode.HOT_PATH
        );
        PipelineTemplate updated = service.updateTemplate(
                created.templateId(), "Updated", "Updated desc", newGraph, Map.of("newKey", "newValue")
        );

        assertEquals("Updated", updated.name());
        assertEquals("Updated desc", updated.description());
        assertEquals(newGraph, updated.graph());
        assertEquals("newValue", updated.defaults().get("newKey"));
    }

    @Test
    void deleteTemplate() {
        PipelineTemplateService service = new PipelineTemplateService();
        PipelineGraph graph = createTestGraph();

        PipelineTemplate created = service.createTemplate(
                "Test", "Desc", PipelineType.SCANNER, graph,
                new PipelineVersion(1, 0, 0), Map.of()
        );

        service.deleteTemplate(created.templateId());

        assertTrue(service.getTemplate(created.templateId()).isEmpty());
    }

    @Test
    void instantiateTemplateCreatesDraft() {
        PipelineTemplateService service = new PipelineTemplateService();
        PipelineGraph graph = createTestGraph();

        PipelineTemplate template = service.createTemplate(
                "Test", "Desc", PipelineType.SCANNER, graph,
                new PipelineVersion(1, 0, 0), Map.of("env", "test")
        );

        PipelineDefinition def = service.instantiateTemplate(template.templateId());

        assertNotNull(def.id());
        assertEquals("Test", def.name());
        assertEquals(PipelineStatus.DRAFT, def.status());
        assertEquals(PipelineType.SCANNER, def.type());
    }

    @Test
    void subscribeToTemplates() {
        PipelineTemplateService service = new PipelineTemplateService();
        var captured = new java.util.concurrent.atomic.AtomicReference<PipelineTemplate>();

        service.subscribe(captured::set);

        PipelineGraph graph = createTestGraph();
        service.createTemplate("Test", "Desc", PipelineType.SCANNER, graph,
                new PipelineVersion(1, 0, 0), Map.of());

        assertNotNull(captured.get());
        assertEquals("Test", captured.get().name());
    }

    @Test
    void updateNonExistentTemplateThrows() {
        PipelineTemplateService service = new PipelineTemplateService();

        assertThrows(IllegalArgumentException.class, () ->
                service.updateTemplate(UUID.randomUUID(), "name", "desc", null, Map.of())
        );
    }

    @Test
    void instantiateNonExistentTemplateThrows() {
        PipelineTemplateService service = new PipelineTemplateService();

        assertThrows(IllegalArgumentException.class, () ->
                service.instantiateTemplate(UUID.randomUUID())
        );
    }
}