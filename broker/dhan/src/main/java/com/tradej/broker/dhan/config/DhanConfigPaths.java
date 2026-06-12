package com.tradej.broker.dhan.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves Dhan credential and token-state paths consistently regardless of whether
 * the JVM was started from the repository root or the {@code trade-app} subproject.
 *
 * <p>LOW-2 fix: when a path is resolved for a credential or token file and the
 * file does not exist, we now log a WARN with the resolved absolute path so
 * operators can immediately see where the system is looking. This is
 * intentionally non-fatal: the caller decides what to do (some paths, e.g.
 * the token state file, are created lazily on first mint).
 */
public final class DhanConfigPaths {

    private static final Logger log = LoggerFactory.getLogger(DhanConfigPaths.class);

    private DhanConfigPaths() {
    }

    public static Path resolve(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalArgumentException("Configured path is blank");
        }
        Path path = Path.of(configuredPath.trim());
        Path resolved = path.isAbsolute()
                ? path.normalize()
                : workspaceRoot().resolve(path).normalize();
        if (!Files.exists(resolved)) {
            log.warn(
                    "Dhan config path resolved to {} but no file exists there. "
                            + "If this is a credential (pin, totp secret), place the file at "
                            + "this absolute path. If this is a state file (token state), it will be "
                            + "created on first use.",
                    resolved);
        }
        return resolved;
    }

    public static Path workspaceRoot() {
        String workspaceOverride = System.getProperty("tradej.workspace.root");
        if (workspaceOverride != null && !workspaceOverride.isBlank()) {
            return Path.of(workspaceOverride).toAbsolutePath().normalize();
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
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
