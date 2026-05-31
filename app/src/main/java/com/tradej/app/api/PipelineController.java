package com.tradej.app.api;

import com.tradej.app.config.ScanProperties;
import com.tradej.app.pipeline.DagPipelineRuntimeService;
import com.tradej.app.pipeline.PipelineRuntimeService;
import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/pipeline")
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

    private final PipelineRuntimeService pipelineRuntimeService;
    private final DagPipelineRuntimeService dagPipelineRuntimeService;
    private final NodeRegistry nodeRegistry;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private static final long METRICS_POLL_INTERVAL_MS = 1500L;

    private final ScheduledExecutorService metricsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "pipeline-metrics-sse");
        thread.setDaemon(true);
        return thread;
    });

    public PipelineController(
            PipelineRuntimeService pipelineRuntimeService,
            DagPipelineRuntimeService dagPipelineRuntimeService,
            NodeRegistry nodeRegistry
    ) {
        this.pipelineRuntimeService = pipelineRuntimeService;
        this.dagPipelineRuntimeService = dagPipelineRuntimeService;
        this.nodeRegistry = nodeRegistry;
        metricsScheduler.scheduleAtFixedRate(this::broadcastMetrics, METRICS_POLL_INTERVAL_MS, METRICS_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @GetMapping("/templates")
    public Map<String, PipelineGraph> graphTemplates(ScanProperties scanProperties) {
        String profileId = scanProperties.defaultProfile() == null ? "default" : scanProperties.defaultProfile();
        return PipelineRuntimeService.graphTemplates(profileId);
    }

    // ── Node Type Registry ───────────────────────────────────────────────

    @GetMapping("/node-types")
    public Map<String, Map<String, Object>> getNodeTypes() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (var entry : nodeRegistry.all().entrySet()) {
            result.put(entry.getKey(), descriptorView(entry.getValue()));
        }
        return result;
    }

    @GetMapping("/node-types/categories")
    public List<String> getNodeTypeCategories() {
        return nodeRegistry.categories();
    }

    @GetMapping("/node-types/category/{category}")
    public List<Map<String, Object>> getNodeTypesByCategory(@PathVariable String category) {
        return nodeRegistry.byCategory(category).stream()
                .map(this::descriptorView)
                .toList();
    }

    /**
     * Converts a NodeTypeDescriptor to a serializable map, excluding the
     * non-serializable {@code factory} function field.
     */
    private Map<String, Object> descriptorView(NodeTypeDescriptor d) {
        Map<String, Object> view = new HashMap<>();
        view.put("typeId", d.typeId());
        view.put("displayName", d.displayName());
        view.put("category", d.category());
        view.put("description", d.description());
        view.put("inputEvents", d.inputEvents().stream()
                .map(e -> Map.of("type", e.type().getSimpleName(), "description", e.description()))
                .toList());
        view.put("outputEvents", d.outputEvents().stream()
                .map(e -> Map.of("type", e.type().getSimpleName(), "description", e.description()))
                .toList());
        view.put("configFields", d.configFields().values().stream()
                .map(f -> {
                    Map<String, Object> fv = new HashMap<>();
                    fv.put("key", f.key());
                    fv.put("type", f.type().name());
                    fv.put("label", f.label());
                    fv.put("defaultValue", f.defaultValue());
                    String[] options = f.options();
                    fv.put("options", options != null && options.length > 0 ? List.of(options) : List.of());
                    return fv;
                })
                .toList());
        return view;
    }

    // ── Active Graph ─────────────────────────────────────────────────────

    @GetMapping("/graph")
    public PipelineGraph getActiveGraph() {
        return pipelineRuntimeService.activeGraph();
    }

    @GetMapping("/dag/graphs")
    public Map<String, PipelineGraph> activeDagGraphs() {
        return dagPipelineRuntimeService.activeGraphs();
    }

    @GetMapping("/dag/graph/{graphId}")
    public PipelineGraph getDagGraph(@PathVariable String graphId) {
        return dagPipelineRuntimeService.getGraph(graphId)
                .orElseThrow(() -> new IllegalArgumentException("DAG graph not active: " + graphId));
    }

    @GetMapping("/history/{graphId}")
    public List<DuckDbPipelineGraphStore.PipelineGraphVersion> listVersions(@PathVariable String graphId) {
        return pipelineRuntimeService.listGraphVersions(graphId);
    }

    @GetMapping("/history/{graphId}/{version}")
    public PipelineGraph loadVersion(@PathVariable String graphId, @PathVariable int version) {
        return pipelineRuntimeService.loadGraphVersion(graphId, version);
    }

    @PostMapping("/persist")
    public Map<String, Object> persistActiveGraph() {
        pipelineRuntimeService.persistActiveGraph();
        PipelineGraph graph = pipelineRuntimeService.activeGraph();
        return Map.of(
                "success", true,
                "graphId", graph.id(),
                "version", graph.version(),
                "executionMode", graph.executionMode().name()
        );
    }

    @PostMapping("/compile")
    public Map<String, Object> compileAndReload(@RequestBody PipelineGraph newGraph) {
        log.info("Compiling pipeline graph: id={} name={} mode={}", newGraph.id(), newGraph.name(), newGraph.executionMode());
        try {
            if (newGraph.executionMode() == PipelineExecutionMode.DAG) {
                dagPipelineRuntimeService.reloadFromApi(newGraph);
            } else {
                pipelineRuntimeService.reloadFromApi(newGraph);
            }
            return Map.of(
                    "success", true,
                    "message", "Pipeline compiled successfully",
                    "version", newGraph.version(),
                    "executionMode", newGraph.executionMode().name()
            );
        } catch (Exception e) {
            log.error("Failed to compile pipeline graph", e);
            return Map.of("success", false, "message", "Compilation failed: " + e.getMessage());
        }
    }

    @PostMapping("/dag/compile")
    public Map<String, Object> compileDagGraph(@RequestBody PipelineGraph newGraph) {
        if (newGraph.executionMode() != PipelineExecutionMode.DAG) {
            return Map.of("success", false, "message", "Graph executionMode must be DAG");
        }
        return compileAndReload(newGraph);
    }

    @PostMapping("/restore/{graphId}/{version}")
    public Map<String, Object> restoreVersion(@PathVariable String graphId, @PathVariable int version) {
        PipelineGraph graph = pipelineRuntimeService.loadGraphVersion(graphId, version);
        if (graph.executionMode() == PipelineExecutionMode.DAG) {
            dagPipelineRuntimeService.reloadFromApi(graph);
        } else {
            pipelineRuntimeService.reloadFromApi(graph);
        }
        return Map.of(
                "success", true,
                "graphId", graph.id(),
                "version", graph.version(),
                "executionMode", graph.executionMode().name()
        );
    }

    @GetMapping(value = "/stream/metrics", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("true"));
            emitter.send(SseEmitter.event().name("pipeline-metrics").data(combinedMetrics()));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    private void broadcastMetrics() {
        if (emitters.isEmpty()) {
            return;
        }
        Map<String, Object> payload = combinedMetrics();
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("pipeline-metrics").data(payload));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    private Map<String, Object> combinedMetrics() {
        Map<String, Object> payload = new HashMap<>(pipelineRuntimeService.metricsSnapshot());
        payload.put("dag", dagPipelineRuntimeService.metricsSnapshot());
        return payload;
    }
}
