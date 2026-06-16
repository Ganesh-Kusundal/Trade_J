package com.tradej.app.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared test utility for resolving runtime warehouse paths in integration tests.
 *
 * <p>Integration tests that need a pre-populated DuckDB warehouse resolve their read-only
 * data path through {@link #runtimeDir()}, which uses a 3-tier fallback:
 * <ol>
 *   <li>System property {@code -Dwarehouse.runtime-dir=...}</li>
 *   <li>Environment variable {@code WAREHOUSE_RUNTIME_DIR}</li>
 *   <li>Default {@code Path.of("runtime-dev")} relative to the working directory</li>
 * </ol>
 *
 * <p>For write targets (e.g. a runtime DuckDB that the engine will create on open), use
 * {@link #tempRuntimeDir(String)} which creates a fresh directory under
 * {@code java.io.tmpdir} — this prevents tests from polluting the dev {@code runtime-dev/}
 * tree with empty fixture files.
 *
 * <p>Pair with {@code @EnabledIfSystemProperty(named = "warehouse.integration.enabled", matches = "true")}
 * to make the test opt-in and prevent accidental CI failures against a stale warehouse.
 */
public final class TestPaths {

    private TestPaths() {}

    /**
     * Resolves the read-only runtime directory for integration test data warehouses.
     *
     * @return the resolved path (never null)
     */
    public static Path runtimeDir() {
        String fromProp = System.getProperty("warehouse.runtime-dir");
        if (fromProp != null && !fromProp.isBlank()) {
            return Path.of(fromProp);
        }
        String fromEnv = System.getenv("WAREHOUSE_RUNTIME_DIR");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Path.of(fromEnv);
        }
        return Path.of("runtime-dev");
    }

    /**
     * Creates and returns a fresh, writable temp directory under {@code java.io.tmpdir}
     * for use as a runtime write target in integration tests. Prevents pollution of the
     * dev {@code runtime-dev/} tree.
     *
     * @param prefix directory name prefix (e.g. {@code "federation-runtime"})
     * @return the created directory path
     * @throws RuntimeException if the directory cannot be created
     */
    public static Path tempRuntimeDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp runtime dir: " + prefix, e);
        }
    }
}
