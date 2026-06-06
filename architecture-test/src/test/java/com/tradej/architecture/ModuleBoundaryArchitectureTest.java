package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@Tag("architecture")
class ModuleBoundaryArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importAllClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    void coreMustNotDependOnOuterModules() {
        noClasses()
                .that().resideInAPackage("com.tradej.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tradej.broker..",
                        "com.tradej.execution..",
                        "com.tradej.strategy..",
                        "com.tradej.disruptor..",
                        "com.tradej.persistence..",
                        "com.tradej.hotpath..",
                        "com.tradej.feature..",
                        "com.tradej.scanner..",
                        "com.tradej.institutional..",
                        "com.tradej.indicators..",
                        "com.tradej.historical..",
                        "com.tradej.composition..",
                        "com.tradej.app.."
                )
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void brokerApiMustNotDependOnOuterModules() {
        noClasses()
                .that().resideInAPackage("com.tradej.broker.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tradej.broker.core..",
                        "com.tradej.broker.dhan..",
                        "com.tradej.broker.upstox..",
                        "com.tradej.broker.icici..",
                        "com.tradej.execution..",
                        "com.tradej.strategy..",
                        "com.tradej.persistence..",
                        "com.tradej.composition..",
                        "com.tradej.app.."
                )
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void historicalIngestMustNotDependOnApp() {
        noClasses()
                .that().resideInAPackage("com.tradej.historical..")
                .should().dependOnClassesThat().resideInAnyPackage("com.tradej.app..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void replayEngineMustNotDependOnLiveBrokers() {
        noClasses()
                .that().resideInAPackage("com.tradej.replay..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tradej.broker.dhan..",
                        "com.tradej.broker.upstox..",
                        "com.tradej.broker.icici.."
                )
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    void compositionMustNotDependOnApp() {
        noClasses()
                .that().resideInAPackage("com.tradej.composition..")
                .should().dependOnClassesThat().resideInAnyPackage("com.tradej.app..")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    void pipelineRuntimeMustNotDependOnApp() {
        noClasses()
                .that().resideInAPackage("com.tradej.pipeline.service..")
                .should().dependOnClassesThat().resideInAnyPackage("com.tradej.app..")
                .allowEmptyShould(true)
                .check(allClasses);
    }
}
