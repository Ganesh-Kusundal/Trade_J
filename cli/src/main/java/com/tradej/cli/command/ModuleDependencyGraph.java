package com.tradej.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parses Gradle build files to construct a module dependency graph.
 */
public final class ModuleDependencyGraph {

    private ModuleDependencyGraph() {}

    private static final Pattern PROJECT_DEP = Pattern.compile(
            "(?:implementation|api|testImplementation)\\s+project\\('(:[^']+)'\\)");

    public static Map<String, List<String>> buildGraph() {
        String workspaceRoot = System.getProperty("trade.workspace.root", ".");
        List<CliModulesCommand.ModuleEntry> modules = CliModulesCommand.parseModules();

        Map<String, String> moduleToDir = new LinkedHashMap<>();
        for (var m : modules) {
            moduleToDir.put(m.name(), m.directory());
        }

        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (var entry : moduleToDir.entrySet()) {
            String moduleName = entry.getKey();
            String dir = entry.getValue();
            Path buildFile = Path.of(workspaceRoot, dir, "build.gradle");
            List<String> deps = new ArrayList<>();

            if (Files.exists(buildFile)) {
                try {
                    String content = Files.readString(buildFile);
                    Matcher matcher = PROJECT_DEP.matcher(content);
                    while (matcher.find()) {
                        String dep = matcher.group(1);
                        dep = dep.startsWith(":") ? dep.substring(1) : dep;
                        if (!deps.contains(dep)) {
                            deps.add(dep);
                        }
                    }
                } catch (IOException e) {
                    // skip
                }
            }
            graph.put(moduleName, deps);
        }
        return graph;
    }

    public static String renderTree(Map<String, List<String>> graph, String root) {
        StringBuilder sb = new StringBuilder();
        Set<String> visited = new HashSet<>();
        renderNode(sb, graph, root, "", true, visited);
        return sb.toString();
    }

    private static void renderNode(StringBuilder sb, Map<String, List<String>> graph,
                                    String module, String prefix, boolean isLast,
                                    Set<String> visited) {
        String connector = isLast ? "└── " : "├── ";
        sb.append(prefix).append(connector).append(module);

        List<String> deps = graph.getOrDefault(module, List.of());
        if (!deps.isEmpty()) {
            sb.append(" (").append(deps.size()).append(" deps)");
        }
        sb.append("\n");

        if (visited.contains(module)) {
            if (!deps.isEmpty()) {
                sb.append(prefix).append(isLast ? "    " : "│   ").append("  (circular — omitted)\n");
            }
            return;
        }
        visited.add(module);

        String childPrefix = prefix + (isLast ? "    " : "│   ");
        for (int i = 0; i < deps.size(); i++) {
            boolean last = (i == deps.size() - 1);
            renderNode(sb, graph, deps.get(i), childPrefix, last, visited);
        }
    }

    public static String renderSummary(Map<String, List<String>> graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("  Module Dependency Summary\n\n");
        sb.append(String.format("  %-30s %s%n", "Module", "Dependencies"));
        sb.append("  ").append("─".repeat(60)).append("\n");

        for (var entry : graph.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sb.append(String.format("  %-30s %d: %s%n",
                        entry.getKey(),
                        entry.getValue().size(),
                        String.join(", ", entry.getValue())));
            }
        }

        long totalDeps = graph.values().stream().mapToLong(List::size).sum();
        long withDeps = graph.values().stream().filter(l -> !l.isEmpty()).count();
        sb.append(String.format("%n  Total: %d modules, %d with dependencies, %d dependency edges%n",
                graph.size(), withDeps, totalDeps));
        return sb.toString();
    }
}
