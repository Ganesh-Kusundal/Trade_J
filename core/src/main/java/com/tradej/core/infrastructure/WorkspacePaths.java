package com.tradej.core.infrastructure;

import java.nio.file.Path;

/**
 * Resolves repo-root-relative paths from a declared workspace root.
 * All runtime modules should use this instead of relying on process working directory.
 */
public final class WorkspacePaths {

    private final Path workspaceRoot;

    public WorkspacePaths(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot == null
                ? Path.of("").toAbsolutePath().normalize()
                : workspaceRoot.toAbsolutePath().normalize();
    }

    public static WorkspacePaths fromSystemProperty() {
        String configured = System.getProperty("trade.workspace.root");
        if (configured != null && !configured.isBlank()) {
            return new WorkspacePaths(Path.of(configured));
        }
        return new WorkspacePaths(Path.of("").toAbsolutePath());
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return workspaceRoot;
        }
        Path candidate = Path.of(relativePath);
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        return workspaceRoot.resolve(candidate).normalize();
    }

    public Path historicalEquityRoot(String configuredRoot) {
        return resolve(configuredRoot == null || configuredRoot.isBlank()
                ? "data/historical-equity"
                : configuredRoot);
    }
}
