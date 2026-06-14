package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tradej.gateway.protocol.GatewayTopic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Enforces contract coverage between code and documentation:
 * <ul>
 *   <li>Every {@code @RestController} base path must be documented in {@code docs/openapi.yaml}</li>
 *   <li>Every {@link GatewayTopic} enum constant must appear in {@code GatewayEventBridge} source</li>
 * </ul>
 */
@Tag("architecture")
class ContractCoverageArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    @DisplayName("Every @RestController base path must be documented in docs/openapi.yaml")
    void restControllerBasePathsDocumented() throws IOException {
        Path specPath = Path.of("../docs/openapi.yaml");
        if (!Files.exists(specPath)) {
            specPath = Path.of("docs/openapi.yaml");
        }
        String openApiContent = Files.readString(specPath);

        List<String> undocumented = new ArrayList<>();

        for (JavaClass javaClass : allClasses) {
            if (!javaClass.isAnnotatedWith("org.springframework.web.bind.annotation.RestController")) {
                continue;
            }

            String basePath = null;
            for (JavaAnnotation<?> annotation : javaClass.getAnnotations()) {
                if (annotation.getRawType().getFullName()
                        .equals("org.springframework.web.bind.annotation.RequestMapping")) {
                    basePath = extractFirstPath(annotation.getProperties().get("value"));
                    break;
                }
            }

            if (basePath == null) {
                continue;
            }

            // Skip admin, webhook, MCP, console, and root-only paths
            if (basePath.startsWith("/admin")
                    || basePath.startsWith("/upstox")
                    || basePath.startsWith("/mcp")
                    || basePath.startsWith("/console")
                    || basePath.equals("/")) {
                continue;
            }

            // Check the base path appears in the OpenAPI spec (as "path:" or "path/")
            if (!openApiContent.contains(basePath + ":") && !openApiContent.contains(basePath + "/")) {
                undocumented.add(basePath + " (" + javaClass.getSimpleName() + ")");
            }
        }

        if (!undocumented.isEmpty()) {
            fail("Undocumented @RestController base paths in openapi.yaml:\n  "
                    + String.join("\n  ", undocumented));
        }
    }

    @Test
    @DisplayName("Every GatewayTopic enum constant must be registered in GatewayEventBridge")
    void gatewayTopicEnumCoveredInBridge() throws IOException {
        // Topics published directly (not via the serializer map) are excluded
        Set<String> publishedDirectly = Set.of(
                "PIPELINE_HEALTH",
                "DEPTH_IMBALANCE",
                "HEATMAP_CHUNK",
                "ICEBERG_ALERT",
                "ABSORPTION_ALERT",
                "SR_LEVELS_UPDATE",
                "ORDER_BOOK_SNAPSHOT"
        );

        Path bridgePath = Path.of("gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java");
        if (!Files.exists(bridgePath)) {
            bridgePath = Path.of("../gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java");
        }
        String bridgeSource = Files.readString(bridgePath);

        List<String> missing = new ArrayList<>();
        for (GatewayTopic topic : GatewayTopic.values()) {
            if (publishedDirectly.contains(topic.name())) {
                continue;
            }
            if (!bridgeSource.contains("GatewayTopic." + topic.name())) {
                missing.add(topic.name());
            }
        }

        assertTrue(missing.isEmpty(),
                "GatewayTopic constants not referenced in GatewayEventBridge:\n  "
                        + String.join("\n  ", missing));
    }

    // ── helpers ──

    /**
     * Extracts the first path string from the raw annotation property value.
     * ArchUnit may represent {@code @RequestMapping("/path")} as a {@code String[]},
     * a plain {@code String}, or a generic {@code Object[]}.
     */
    private static String extractFirstPath(Object rawValue) {
        if (rawValue instanceof String[] arr && arr.length > 0) {
            return arr[0];
        }
        if (rawValue instanceof String s) {
            return s;
        }
        if (rawValue instanceof Object[] arr && arr.length > 0) {
            return arr[0].toString();
        }
        return rawValue != null ? rawValue.toString() : null;
    }
}
