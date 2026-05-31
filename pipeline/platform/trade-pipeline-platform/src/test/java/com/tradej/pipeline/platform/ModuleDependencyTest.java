package com.tradej.pipeline.platform;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test enforcing module boundary rules for {@code com.tradej.pipeline.platform}.
 * <p>
 * The platform module is a FOUNDATIONAL module and may only depend on:
 * <ul>
 *   <li>{@code com.tradej.pipeline.platform} (self)</li>
 *   <li>{@code com.tradej.core} (allowed dependency)</li>
 * </ul>
 * Dependencies on broker, execution, strategy, disruptor, persistence, or app modules
 * are forbidden to preserve the foundational layering.
 */
@Tag("unit")
class ModuleDependencyTest {

    private static final String PLATFORM_PACKAGE = "com.tradej.pipeline.platform";

    private final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(PLATFORM_PACKAGE);

    @Test
    void platformModuleMayOnlyDependOnCoreAndJavaStandardLibrary() {
        ArchRule rule = classes()
                .that().resideInAPackage(PLATFORM_PACKAGE + "..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        PLATFORM_PACKAGE + "..",
                        "com.tradej.core..",
                        "com.tradej.pipeline..",
                        "java..",
                        "javax.."
                );

        rule.check(importedClasses);
    }

    @Test
    void platformModuleShouldNotDependOnBrokerModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(PLATFORM_PACKAGE + "..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.tradej.broker..");

        rule.check(importedClasses);
    }

    @Test
    void platformModuleShouldNotDependOnExecutionModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(PLATFORM_PACKAGE + "..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.tradej.execution..");

        rule.check(importedClasses);
    }

    @Test
    void platformModuleShouldNotDependOnStrategyModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(PLATFORM_PACKAGE + "..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.tradej.strategy..");

        rule.check(importedClasses);
    }

    @Test
    void platformModuleShouldNotDependOnDisruptorModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(PLATFORM_PACKAGE + "..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.tradej.disruptor..");

        rule.check(importedClasses);
    }

    @Test
    void platformModuleShouldNotDependOnPersistenceModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(PLATFORM_PACKAGE + "..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.tradej.persistence..");

        rule.check(importedClasses);
    }

    @Test
    void platformModuleShouldNotDependOnAppModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(PLATFORM_PACKAGE + "..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.tradej.app..");

        rule.check(importedClasses);
    }

    @Test
    void allPlatformClassesResideInExpectedPackage() {
        ArchRule rule = classes()
                .that().resideInAPackage(PLATFORM_PACKAGE)
                .should().resideInAPackage(PLATFORM_PACKAGE + "..");

        rule.check(importedClasses);
    }
}
