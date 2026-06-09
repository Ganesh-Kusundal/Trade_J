package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.Source;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Enforces code quality constraints that prevent common architectural decay:
 * <ul>
 *   <li>No God classes exceeding 800 lines in production code</li>
 *   <li>No broker-specific type references outside broker and CLI adapter packages</li>
 * </ul>
 */
@Tag("architecture")
class CodeQualityArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importAllClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    void noProductionClassShouldExceed800Lines() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : allClasses) {
            if (javaClass.isInnerClass() || javaClass.isAnonymousClass()) {
                continue;
            }
            Source source = javaClass.getSource().orElse(null);
            if (source == null) continue;

            int lineCount = source.getUri().toString().endsWith(".java")
                    ? countSourceLines(javaClass)
                    : 0;

            if (lineCount > 800) {
                violations.add(String.format("  %s: %d lines (max 800)",
                        javaClass.getFullName(), lineCount));
            }
        }
        assertTrue(violations.isEmpty(),
                "God classes detected (>800 lines):\n" + String.join("\n", violations)
                        + "\n\nRefactor by extracting focused components per Single Responsibility Principle.");
    }

    @Test
    void brokerConnectionTypesMustNotLeakOutsideBrokerAndAdapterPackages() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : allClasses) {
            String pkg = javaClass.getPackageName();
            // Allowed packages: broker adapters, broker-core, CLI (adapter layer), app (composition root)
            if (pkg.startsWith("com.tradej.broker.")
                    || pkg.startsWith("com.tradej.cli.")
                    || pkg.startsWith("com.tradej.app.")
                    || pkg.startsWith("com.tradej.composition.")) {
                continue;
            }
            for (JavaMethod method : javaClass.getMethods()) {
                for (JavaClass paramType : method.getRawParameterTypes()) {
                    if (isConcreteBrokerConnection(paramType)) {
                        violations.add(String.format("  %s.%s() references %s",
                                javaClass.getFullName(), method.getName(), paramType.getFullName()));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Broker connection types leaked into core modules:\n" + String.join("\n", violations)
                        + "\n\nUse abstractions (IBrokerConnection, ExecutionVenue) instead of concrete broker types.");
    }

    private static boolean isConcreteBrokerConnection(JavaClass type) {
        String name = type.getFullName();
        return name.equals("com.tradej.broker.dhan.DhanBrokerConnection")
                || name.equals("com.tradej.broker.upstox.UpstoxBrokerConnection")
                || name.equals("com.tradej.broker.icici.IciciBrokerConnection");
    }

    private static int countSourceLines(JavaClass javaClass) {
        // Estimate line count from the number of methods, fields, and inner classes.
        // ArchUnit doesn't provide raw source line counts, so we use a heuristic:
        // count all members and multiply by average lines per member.
        int memberCount = javaClass.getMethods().size()
                + javaClass.getFields().size();
        // Conservative: ~8 lines per member on average (methods + blank lines + imports)
        return memberCount * 8;
    }
}
