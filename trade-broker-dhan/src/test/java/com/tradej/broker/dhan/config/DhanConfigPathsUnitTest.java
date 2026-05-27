package com.tradej.broker.dhan.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanConfigPathsUnitTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesRelativePathsAgainstDiscoveredWorkspaceRoot() throws Exception {
        Path workspace = tempDir.resolve("repo");
        Files.createDirectories(workspace.resolve("config"));
        Files.writeString(workspace.resolve("settings.gradle"), "rootProject.name = 'test'");
        Files.writeString(workspace.resolve("config/dhan-local.properties"), "dhan.clientId=1");

        Path previous = Path.of("").toAbsolutePath();
        try {
            System.setProperty("user.dir", workspace.resolve("trade-app").toString());
            Files.createDirectories(workspace.resolve("trade-app"));

            Path resolved = DhanConfigPaths.resolve("runtime/dhan-token-state.json");

            assertEquals(workspace.resolve("runtime/dhan-token-state.json").normalize(), resolved);
            assertEquals(workspace.normalize(), DhanConfigPaths.workspaceRoot().normalize());
        } finally {
            System.setProperty("user.dir", previous.toString());
        }
    }

    @Test
    void leavesAbsolutePathsUnchanged() {
        Path absolute = Path.of("/tmp/dhan-token-state.json");
        assertEquals(absolute.normalize(), DhanConfigPaths.resolve(absolute.toString()));
    }
}
