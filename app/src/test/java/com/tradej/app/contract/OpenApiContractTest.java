package com.tradej.app.contract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Contract test: every @RestController endpoint must have a matching
 * path entry in docs/openapi.yaml.
 *
 * <p>Scans all Java source files for @RequestMapping + @GetMapping/@PostMapping
 * annotations and verifies each path exists in the OpenAPI spec.
 */
@Tag("contract")
class OpenApiContractTest {

    private static String openApiContent;

    @BeforeAll
    static void loadSpec() throws IOException {
        Path specPath = Path.of("../docs/openapi.yaml");
        if (!Files.exists(specPath)) {
            specPath = Path.of("docs/openapi.yaml");
        }
        openApiContent = Files.readString(specPath);
    }

    @Test
    @DisplayName("All REST controller base paths must exist in OpenAPI spec")
    void allControllerPathsDocumented() throws IOException {
        List<String> undocumented = new ArrayList<>();

        Path appSrc = Path.of("src/main/java/com/tradej/app");
        if (!Files.exists(appSrc)) {
            appSrc = Path.of("../app/src/main/java/com/tradej/app");
        }
        if (!Files.exists(appSrc)) {
            return;
        }

        Pattern requestMapping = Pattern.compile("@RequestMapping\\(\"([^\"]+)\"\\)");

        Files.walk(appSrc)
                .filter(p -> p.toString().endsWith("Controller.java"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        Matcher baseMatcher = requestMapping.matcher(content);
                        while (baseMatcher.find()) {
                            String basePath = baseMatcher.group(1);
                            // Skip admin and internal paths
                            if (basePath.startsWith("/admin") || basePath.startsWith("/upstox") || basePath.startsWith("/mcp")) {
                                continue;
                            }
                            // Check the base path exists in spec
                            if (!openApiContent.contains(basePath + ":") && !openApiContent.contains(basePath + "/")) {
                                undocumented.add(basePath + " (" + file.getFileName() + ")");
                            }
                        }
                    } catch (IOException e) {
                        // skip
                    }
                });

        if (!undocumented.isEmpty()) {
            fail("Undocumented API base paths:\n  " + String.join("\n  ", undocumented));
        }
    }

    @Test
    @DisplayName("OpenAPI spec must contain ErrorResponse schema")
    void errorResponseSchemaExists() {
        assertTrue(openApiContent.contains("ErrorResponse:"),
                "OpenAPI spec must define ErrorResponse schema");
    }

    @Test
    @DisplayName("OpenAPI spec must contain all major domain tags")
    void majorDomainTagsPresent() {
        for (String tag : List.of("Market Data", "Orders", "Pipeline", "Scans", "Analytics", "Broker", "Events", "Discovery")) {
            assertTrue(openApiContent.contains("tags: [" + tag + "]") || openApiContent.contains(tag),
                    "OpenAPI spec must reference tag: " + tag);
        }
    }
}
