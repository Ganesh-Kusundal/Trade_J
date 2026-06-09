package com.tradej.cli.command;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ModuleDependencyGraphTest {

    @Test
    void buildGraph_findsModules() {
        Map<String, List<String>> graph = ModuleDependencyGraph.buildGraph();
        assertFalse(graph.isEmpty(), "graph should not be empty");
        assertTrue(graph.containsKey("app"), "app module should be in graph");
        assertTrue(graph.containsKey("cli"), "cli module should be in graph");
        assertTrue(graph.containsKey("core"), "core module should be in graph");
    }

    @Test
    void buildGraph_appHasDependencies() {
        Map<String, List<String>> graph = ModuleDependencyGraph.buildGraph();
        List<String> appDeps = graph.get("app");
        assertNotNull(appDeps, "app should have dependencies");
        assertFalse(appDeps.isEmpty(), "app should have at least one dependency");
        assertTrue(appDeps.contains("core"), "app should depend on core");
    }

    @Test
    void renderTree_producesOutput() {
        Map<String, List<String>> graph = ModuleDependencyGraph.buildGraph();
        String tree = ModuleDependencyGraph.renderTree(graph, "app");
        assertFalse(tree.isEmpty(), "tree should not be empty");
        assertTrue(tree.contains("app"), "tree should contain root module");
    }

    @Test
    void renderSummary_producesOutput() {
        Map<String, List<String>> graph = ModuleDependencyGraph.buildGraph();
        String summary = ModuleDependencyGraph.renderSummary(graph);
        assertFalse(summary.isEmpty());
        assertTrue(summary.contains("Total:"));
    }
}
