package com.tradej.app.workspace;

import com.tradej.core.domain.port.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory workspace manager. Each workspace holds isolated configuration
 * for broker connections, strategies, and risk limits.
 */
@Component
public class InMemoryWorkspaceManager implements WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(InMemoryWorkspaceManager.class);

    private final Map<String, WorkspaceImpl> workspaces = new ConcurrentHashMap<>();
    private volatile String activeWorkspaceName = "default";

    public InMemoryWorkspaceManager() {
        workspaces.put("default", new WorkspaceImpl("default", Map.of(), true));
        log.info("Workspace manager initialized with 'default' workspace");
    }

    @Override
    public Workspace create(String name, Map<String, String> config) {
        WorkspaceImpl ws = new WorkspaceImpl(name, config, false);
        workspaces.put(name, ws);
        log.info("Workspace '{}' created with {} config entries", name, config.size());
        return ws;
    }

    @Override
    public Workspace get(String name) {
        WorkspaceImpl ws = workspaces.get(name);
        if (ws == null) throw new IllegalArgumentException("Workspace not found: " + name);
        return ws;
    }

    @Override
    public Set<String> list() {
        return Set.copyOf(workspaces.keySet());
    }

    @Override
    public void delete(String name) {
        if ("default".equals(name)) throw new IllegalArgumentException("Cannot delete default workspace");
        workspaces.remove(name);
        if (name.equals(activeWorkspaceName)) {
            activeWorkspaceName = "default";
        }
        log.info("Workspace '{}' deleted", name);
    }

    @Override
    public void activate(String name) {
        if (!workspaces.containsKey(name)) throw new IllegalArgumentException("Workspace not found: " + name);
        // Deactivate current
        WorkspaceImpl current = workspaces.get(activeWorkspaceName);
        if (current != null) {
            workspaces.put(activeWorkspaceName, new WorkspaceImpl(current.name, current.configMap, false));
        }
        // Activate new
        WorkspaceImpl target = workspaces.get(name);
        workspaces.put(name, new WorkspaceImpl(target.name, target.configMap, true));
        activeWorkspaceName = name;
        log.info("Activated workspace '{}'", name);
    }

    @Override
    public Workspace active() {
        return workspaces.get(activeWorkspaceName);
    }

    @Override
    public Map<String, Object> export(String name) {
        WorkspaceImpl ws = workspaces.get(name);
        if (ws == null) throw new IllegalArgumentException("Workspace not found: " + name);
        Map<String, Object> exported = new LinkedHashMap<>();
        exported.put("name", ws.name);
        exported.put("config", ws.configMap);
        return exported;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Workspace importWorkspace(String name, Map<String, Object> data) {
        Map<String, String> config = (Map<String, String>) data.getOrDefault("config", Map.of());
        return create(name, config);
    }

    static final class WorkspaceImpl implements Workspace {
        final String name;
        final Map<String, String> configMap;
        final boolean isActive;

        WorkspaceImpl(String name, Map<String, String> config, boolean isActive) {
            this.name = name;
            this.configMap = Map.copyOf(config);
            this.isActive = isActive;
        }

        @Override public String name() { return name; }
        @Override public Map<String, String> config() { return configMap; }
        @Override public boolean isActive() { return isActive; }
    }
}
