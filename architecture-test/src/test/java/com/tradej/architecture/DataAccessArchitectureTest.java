package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces data access boundaries: only persistence modules may access
 * DuckDB directly. Application, CLI, and runtime modules must use port
 * interfaces defined in the core module.
 */
@Tag("architecture")
class DataAccessArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importAllClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    void controllersMustNotAccessDuckDbDirectly() {
        assertNoDuckDbInPackage("com.tradej.app.api",
                "REST controllers must not access DuckDB directly. "
                        + "Use port interfaces (HistoricalBarRepository, HistoricalAnalyticsService) instead.");
    }

    @Test
    void runtimeDisruptorMustNotAccessDuckDbDirectly() {
        assertNoDuckDbInPackage("com.tradej.disruptor",
                "Disruptor module must not access DuckDB directly. "
                        + "Event persistence belongs in the persistence module.");
    }

    private void assertNoDuckDbInPackage(String packagePrefix, String message) {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : allClasses) {
            String pkg = javaClass.getPackageName();
            if (!pkg.startsWith(packagePrefix)) {
                continue;
            }
            for (JavaMethod method : javaClass.getMethods()) {
                for (JavaClass paramType : method.getRawParameterTypes()) {
                    if (isDuckDbType(paramType.getFullName())) {
                        violations.add(String.format("  %s.%s() parameter %s",
                                javaClass.getFullName(), method.getName(), paramType.getFullName()));
                    }
                }
                if (isDuckDbType(method.getRawReturnType().getFullName())) {
                    violations.add(String.format("  %s.%s() returns %s",
                            javaClass.getFullName(), method.getName(), method.getRawReturnType().getFullName()));
                }
            }
            for (JavaField field : javaClass.getFields()) {
                if (isDuckDbType(field.getRawType().getFullName())) {
                    violations.add(String.format("  %s.%s field type %s",
                            javaClass.getFullName(), field.getName(), field.getRawType().getFullName()));
                }
            }
        }
        assertTrue(violations.isEmpty(), message + "\nViolations:\n" + String.join("\n", violations));
    }

    private static boolean isDuckDbType(String fullName) {
        return fullName.startsWith("com.tradej.persistence.duckdb.DuckDb")
                || fullName.startsWith("org.duckdb.");
    }
}
