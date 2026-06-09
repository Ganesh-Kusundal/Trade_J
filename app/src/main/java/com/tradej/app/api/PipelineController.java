package com.tradej.app.api;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/v1/pipeline")
public class PipelineController {

    private static final long METRICS_POLL_INTERVAL_MS = 1500L;

    public PipelineController() {}

    @GetMapping("/templates")
    public Map<String, Object> graphTemplates() {
        return Map.of();
    }

    @GetMapping("/node-types")
    public Map<String, Object> getNodeTypes() {
        return Map.of();
    }

    @GetMapping("/node-types/categories")
    public List<String> getNodeTypeCategories() {
        return List.of();
    }

    @GetMapping("/graph")
    public Map<String, Object> getActiveGraph() {
        return Map.of("id", "placeholder", "version", 0, "nodes", List.of());
    }
}