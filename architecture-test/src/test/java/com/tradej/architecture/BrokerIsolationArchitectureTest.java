package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces broker isolation: composition module must not import concrete
 * broker connection types (except BrokerComposition for idempotency cache).
 */
@Tag("architecture")
class BrokerIsolationArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importAllClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    void compositionMustNotImportConcreteBrokerConnectionTypes() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : allClasses) {
            String pkg = javaClass.getPackageName();
            if (!pkg.startsWith("com.tradej.brokergateway")) {
                continue;
            }
            if (javaClass.getSimpleName().equals("BrokerComposition")
                    || javaClass.getSimpleName().equals("UpstoxBrokerFactory")
                    || javaClass.getSimpleName().equals("IciciBrokerFactory")) {
                continue;
            }
            for (JavaMethod method : javaClass.getMethods()) {
                for (JavaClass paramType : method.getRawParameterTypes()) {
                    if (isConcreteBrokerConnection(paramType)) {
                        violations.add(String.format("  %s.%s() references %s",
                                javaClass.getFullName(), method.getName(), paramType.getFullName()));
                    }
                }
                if (isConcreteBrokerConnection(method.getRawReturnType())) {
                    violations.add(String.format("  %s.%s() returns %s",
                            javaClass.getFullName(), method.getName(), method.getRawReturnType().getFullName()));
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Composition module must not reference concrete broker connection types "
                        + "(except BrokerComposition for idempotency cache):\n"
                        + String.join("\n", violations)
                        + "\n\nUse BrokerProvider SPI or IBrokerConnection interface instead.");
    }

    @Test
    void appControllersMustNotReferenceBrokerSpecificCapabilities() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : allClasses) {
            String pkg = javaClass.getPackageName();
            if (!pkg.startsWith("com.tradej.app.api")) {
                continue;
            }
            for (JavaMethod method : javaClass.getMethods()) {
                for (JavaClass paramType : method.getRawParameterTypes()) {
                    if (paramType.getFullName().contains("HistoricalDataCapabilities")) {
                        violations.add(String.format("  %s.%s() references %s",
                                javaClass.getFullName(), method.getName(), paramType.getFullName()));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Controllers must not reference broker-specific capabilities directly:\n"
                        + String.join("\n", violations)
                        + "\n\nUse MarketGateway.capabilities() for capability discovery.");
    }

    @Test
    void controllersMustNotInjectIBrokerConnection() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : allClasses) {
            String pkg = javaClass.getPackageName();
            if (!pkg.startsWith("com.tradej.app.api") && !pkg.startsWith("com.tradej.app.admin")) {
                continue;
            }
            for (JavaConstructor ctor : javaClass.getConstructors()) {
                for (JavaClass paramType : ctor.getRawParameterTypes()) {
                    if (paramType.getFullName().equals("com.tradej.broker.api.IBrokerConnection")) {
                        violations.add(String.format("  %s constructor injects IBrokerConnection",
                                javaClass.getFullName()));
                    }
                }
            }
            for (JavaField field : javaClass.getFields()) {
                if (field.getRawType().getFullName().equals("com.tradej.broker.api.IBrokerConnection")) {
                    violations.add(String.format("  %s.%s field is IBrokerConnection",
                            javaClass.getFullName(), field.getName()));
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Controllers must not inject IBrokerConnection directly. "
                        + "Use application services (MarketDataApplicationService, AdminApplicationService) instead:\n"
                        + String.join("\n", violations));
    }

    @Test
    void brokerConfigurationMustNotImportDhanSpecificTypes() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : allClasses) {
            if (!javaClass.getFullName().equals("com.tradej.app.config.BrokerConfiguration")) {
                continue;
            }
            for (JavaMethod method : javaClass.getMethods()) {
                for (JavaClass paramType : method.getRawParameterTypes()) {
                    if (isDhanSpecific(paramType.getFullName())) {
                        violations.add(String.format("  %s.%s() references %s",
                                javaClass.getFullName(), method.getName(), paramType.getFullName()));
                    }
                }
                if (isDhanSpecific(method.getRawReturnType().getFullName())) {
                    violations.add(String.format("  %s.%s() returns %s",
                            javaClass.getFullName(), method.getName(), method.getRawReturnType().getFullName()));
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "BrokerConfiguration must be broker-agnostic. Dhan-specific types belong in DhanBrokerConfiguration:\n"
                        + String.join("\n", violations));
    }

    @Test
    void disruptorEventBusHasSinglePublicConstructor() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : allClasses) {
            if (!javaClass.getFullName().equals("com.tradej.disruptor.DisruptorEventBus")) {
                continue;
            }
            long publicCtorCount = javaClass.getConstructors().stream()
                    .filter(c -> c.getModifiers().contains(JavaModifier.PUBLIC))
                    .count();
            if (publicCtorCount != 1) {
                violations.add(String.format("  DisruptorEventBus has %d public constructors (expected 1)",
                        publicCtorCount));
            }
        }
        assertTrue(violations.isEmpty(),
                "DisruptorEventBus must have exactly 1 public constructor (DisruptorPipelineConfig):\n"
                        + String.join("\n", violations));
    }

    private static boolean isDhanSpecific(String fullName) {
        return fullName.startsWith("com.tradej.broker.dhan.")
                && !fullName.equals("com.tradej.broker.dhan.DhanBrokerConnection");
    }

    private static boolean isConcreteBrokerConnection(JavaClass type) {
        String name = type.getFullName();
        return name.equals("com.tradej.broker.dhan.DhanBrokerConnection")
                || name.equals("com.tradej.broker.upstox.UpstoxBrokerConnection")
                || name.equals("com.tradej.broker.icici.IciciBrokerConnection");
    }
}
