package com.tradej.app.workspace;

import com.tradej.core.domain.port.WorkspaceManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceManager workspaceManager;

    public WorkspaceController(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @GetMapping
    public List<String> list() {
        return List.copyOf(workspaceManager.list());
    }

    @GetMapping("/active")
    public Map<String, Object> active() {
        WorkspaceManager.Workspace ws = workspaceManager.active();
        return Map.of("name", ws.name(), "config", ws.config(), "active", ws.isActive());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        @SuppressWarnings("unchecked")
        Map<String, String> config = (Map<String, String>) request.getOrDefault("config", Map.of());
        WorkspaceManager.Workspace ws = workspaceManager.create(name, config);
        return Map.of("name", ws.name(), "config", ws.config());
    }

    @PostMapping("/{name}/activate")
    public Map<String, String> activate(@PathVariable String name) {
        workspaceManager.activate(name);
        return Map.of("activated", name);
    }

    @DeleteMapping("/{name}")
    public Map<String, String> delete(@PathVariable String name) {
        workspaceManager.delete(name);
        return Map.of("deleted", name);
    }

    @GetMapping("/{name}/export")
    public Map<String, Object> export(@PathVariable String name) {
        return workspaceManager.export(name);
    }

    @PostMapping("/import")
    public Map<String, Object> importWorkspace(@RequestBody Map<String, Object> data) {
        String name = (String) data.get("name");
        WorkspaceManager.Workspace ws = workspaceManager.importWorkspace(name, data);
        return Map.of("name", ws.name(), "config", ws.config());
    }
}
