package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces ownership boundaries between the three event systems:
 * <ol>
 *   <li><b>Disruptor</b> ({@code runtime/disruptor}) — hot-path, low-latency event bus</li>
 *   <li><b>Reactor</b> ({@code pipeline/core/reactor}) — cold-path, async pipeline processing</li>
 *   <li><b>EventBus</b> ({@code core/domain/port}) — business events port used by domain modules</li>
 * </ol>
 *
 * <p>Each system owns a specific layer. Cross-contamination is an architectural smell.
 */
@Tag("architecture")
class EventSystemOwnershipTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    @DisplayName("Domain and strategy modules must not depend on Disruptor internals")
    void domainMustNotDependOnDisruptor() {
        noClasses()
                .that().resideInAPackage("com.tradej.core.domain..")
                .or().resideInAPackage("com.tradej.strategy..")
                .or().resideInAPackage("com.tradej.execution..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.tradej.disruptor..")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Domain and strategy modules must not depend on Reactor internals")
    void domainMustNotDependOnReactor() {
        noClasses()
                .that().resideInAPackage("com.tradej.core.domain..")
                .or().resideInAPackage("com.tradej.strategy..")
                .or().resideInAPackage("com.tradej.execution..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.tradej.pipeline..reactor..")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Disruptor module must not depend on Reactor")
    void disruptorMustNotDependOnReactor() {
        noClasses()
                .that().resideInAPackage("com.tradej.disruptor..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.tradej.pipeline..reactor..")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Reactor bridge must not depend on Disruptor")
    void reactorMustNotDependOnDisruptor() {
        noClasses()
                .that().resideInAPackage("com.tradej.pipeline..reactor..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.tradej.disruptor..")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Domain modules should only use the EventBus port interface")
    void domainShouldOnlyUseEventBusPort() {
        noClasses()
                .that().resideInAPackage("com.tradej.core.domain..")
                .should().dependOnClassesThat()
                .haveSimpleName("DisruptorEventBus")
                .orShould().dependOnClassesThat()
                .haveSimpleName("ShardedDisruptorEventBus")
                .orShould().dependOnClassesThat()
                .haveSimpleName("ReactorBridge")
                .allowEmptyShould(true)
                .check(allClasses);
    }
}
