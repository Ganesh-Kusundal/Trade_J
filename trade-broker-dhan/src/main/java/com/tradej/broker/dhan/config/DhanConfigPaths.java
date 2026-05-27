package com.tradej.broker.dhan.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves Dhan credential and token-state paths consistently regardless of whether
 * the JVM was started from the repository root or the {@code trade-app} subproject.
 */
public final class DhanConfigPaths {
    private DhanConfigPaths() {
    }

    public static Path resolve(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalArgumentException("Configured path is blank");
        }
        Path path = Path.of(configuredPath.trim());
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return workspaceRoot().resolve(path).normalize();
    }

    public static Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle"))
                    || Files.exists(current.resolve("config/dhan-local.properties"))) {
                return current;
            }
            current = current.getParent();
        }
        return Path.of("").toAbsolutePath().normalize();
    }
}
