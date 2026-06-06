package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces that core business modules have zero Spring Framework dependencies.
 * These modules must remain framework-agnostic and portable across runtimes
 * (Spring Boot app, CLI, embedded, test).
 */
@Tag("architecture")
class SpringFreeArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importAllClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    void coreModulesMustNotImportSpring() {
        noClasses()
                .that().resideInAnyPackage(
                        "com.tradej.core..",
                        "com.tradej.broker.api..",
                        "com.tradej.broker.core..",
                        "com.tradej.broker.dhan..",
                        "com.tradej.broker.upstox..",
                        "com.tradej.broker.icici..",
                        "com.tradej.execution..",
                        "com.tradej.strategy..",
                        "com.tradej.scanner..",
                        "com.tradej.institutional..",
                        "com.tradej.indicators..",
                        "com.tradej.options..",
                        "com.tradej.persistence..",
                        "com.tradej.feature..",
                        "com.tradej.historical..",
                        "com.tradej.analytics..",
                        "com.tradej.disruptor..",
                        "com.tradej.hotpath..",
                        "com.tradej.pipeline.runtime..",
                        "com.tradej.pipeline.service..",
                        "com.tradej.pipeline.graph..",
                        "com.tradej.pipeline.clock..",
                        "com.tradej.pipeline.reactor..",
                        "com.tradej.pipeline.registry..",
                        "com.tradej.replay.engine.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.annotation..",
                        "jakarta.validation.."
                )
                .because("Core business modules must be Spring-free to support CLI, embedded, and test runtimes")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    void coreModulesMustNotUseSpringAnnotations() {
        noClasses()
                .that().resideInAnyPackage(
                        "com.tradej.core..",
                        "com.tradej.broker.api..",
                        "com.tradej.broker.core..",
                        "com.tradej.broker.dhan..",
                        "com.tradej.broker.upstox..",
                        "com.tradej.broker.icici..",
                        "com.tradej.execution..",
                        "com.tradej.strategy..",
                        "com.tradej.scanner..",
                        "com.tradej.institutional..",
                        "com.tradej.indicators..",
                        "com.tradej.options..",
                        "com.tradej.persistence..",
                        "com.tradej.feature..",
                        "com.tradej.historical..",
                        "com.tradej.analytics..",
                        "com.tradej.disruptor..",
                        "com.tradej.hotpath..",
                        "com.tradej.pipeline.runtime..",
                        "com.tradej.pipeline.service..",
                        "com.tradej.pipeline.graph..",
                        "com.tradej.pipeline.clock..",
                        "com.tradej.pipeline.reactor..",
                        "com.tradej.pipeline.registry..",
                        "com.tradej.replay.engine..",
                        "com.tradej.composition.."
                )
                .should().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration")
                .orShould().beAnnotatedWith("org.springframework.context.annotation.Bean")
                .because("Spring annotations belong only in the app module (composition root)")
                .allowEmptyShould(true)
                .check(allClasses);
    }
}
