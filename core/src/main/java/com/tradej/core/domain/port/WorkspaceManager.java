package com.tradej.core.domain.port;

import java.util.Map;
import java.util.Set;

/**
 * Manages isolated workspaces for strategy development and trading.
 * Each workspace has its own broker configuration, strategies, risk limits,
 * and persisted state.
 */
public interface WorkspaceManager {

    /**
     * Create a new workspace with the given name and configuration.
     */
    Workspace create(String name, Map<String, String> config);

    /**
     * Get a workspace by name.
     */
    Workspace get(String name);

    /**
     * List all workspace names.
     */
    Set<String> list();

    /**
     * Delete a workspace and its associated state.
     */
    void delete(String name);

    /**
     * Switch the active workspace.
     */
    void activate(String name);

    /**
     * Get the currently active workspace.
     */
    Workspace active();

    /**
     * Export workspace configuration as a map for backup/sharing.
     */
    Map<String, Object> export(String name);

    /**
     * Import workspace configuration from a previously exported map.
     */
    Workspace importWorkspace(String name, Map<String, Object> data);

    /**
     * Represents a single isolated workspace.
     */
    interface Workspace {
        String name();
        Map<String, String> config();
        boolean isActive();
    }
}
