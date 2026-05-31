package com.tradej.core.infrastructure;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class WorkspacePathsTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesRelativePathsFromWorkspaceRoot() {
        WorkspacePaths paths = new WorkspacePaths(tempDir);
        Path historical = paths.historicalEquityRoot("data/historical-equity");
        assertEquals(tempDir.resolve("data/historical-equity").normalize(), historical);
        assertTrue(paths.resolve("config/dhan-local.properties").startsWith(tempDir));
    }
}
