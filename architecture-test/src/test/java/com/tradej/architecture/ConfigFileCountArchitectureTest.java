package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces that the Spring configuration file count in
 * {@code com.tradej.app.config} stays bounded.
 *
 * <p>When this test fails, the developer should consolidate
 * small config classes into domain-grouped files (broker, data,
 * execution, pipeline, gateway, observability, etc.) rather
 * than adding new top-level {@code @Configuration} classes.
 */
@Tag("architecture")
class ConfigFileCountArchitectureTest {

    /**
     * Maximum number of {@code @Configuration} classes allowed
     * in the app config package. Update this ONLY when consolidating,
     * never when adding new files.
     *
     * <p>Current count (2026-06-09): 15
     */
    private static final int MAX_CONFIG_CLASSES = 15;

    private static JavaClasses configClasses;

    @BeforeAll
    static void importConfigClasses() {
        configClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("com.tradej.app.config");
    }

    @Test
    void configurationFileCountMustStayBelowLimit() {
        // Only count top-level @Configuration classes (actual .java files),
        // not inner static classes like DhanAdapterConfig, GatewayConfig, etc.
        // In compiled bytecode, inner classes have '$' in their name (e.g., BrokerConfiguration$DhanAdapterConfig)
        Set<JavaClass> configurationClasses = configClasses.stream()
                .filter(c -> c.isAnnotatedWith("org.springframework.context.annotation.Configuration"))
                .filter(c -> !c.getName().contains("$"))
                .collect(Collectors.toSet());

        List<String> configNames = configurationClasses.stream()
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        assertTrue(
                configurationClasses.size() <= MAX_CONFIG_CLASSES,
                String.format(
                        "Too many top-level @Configuration classes in com.tradej.app.config: %d (max %d).\n"
                                + "\n"
                                + "Current files (%d):\n"
                                + "  %s\n"
                                + "\n"
                                + "To fix: consolidate small config classes into domain-grouped files "
                                + "(broker, data, execution, pipeline, gateway, observability) "
                                + "rather than adding new top-level @Configuration classes.\n"
                                + "If you genuinely need a new config file, increase MAX_CONFIG_CLASSES "
                                + "and document the justification in the commit message.",
                        configurationClasses.size(),
                        MAX_CONFIG_CLASSES,
                        configurationClasses.size(),
                        String.join("\n  ", configNames)
                )
        );
    }

}
