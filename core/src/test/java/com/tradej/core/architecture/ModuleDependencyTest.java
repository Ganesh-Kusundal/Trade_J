package com.tradej.core.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * ArchUnit tests that enforce module boundary rules across the Trade-J platform.
 * <p>
 * These tests prevent architectural drift by ensuring core modules don't
 * accumulate dependencies on higher-level modules.
 * <p>
 * Run via: {@code ./gradlew :core:test --tests '*ModuleDependencyTest'}
 * or include in the regular {@code unitTest} task via the {@code unit} tag.
 */
@Tag("unit")
class ModuleDependencyTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importAllClasses() {
        // Import only Trade-J module classes — avoids scanning JDK/JAR classes into memory.
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    // ── Core layer ───────────────────────────────────────────────

    @Test
    void coreModuleMustNotDependOnAnyOtherTradeJModule() {
        noClasses()
                .that().resideInAPackage("com.tradej.core..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.broker..",
                        "com.tradej.execution..",
                        "com.tradej.strategy..",
                        "com.tradej.disruptor..",
                        "com.tradej.persistence..",
                        "com.tradej.hotpath..",
                        "com.tradej.feature..",
                        "com.tradej.gateway..",
                        "com.tradej.simulation..",
                        "com.tradej.cli..",
                        "com.tradej.app.."
                )
                .allowEmptyShould(true)
                .because("core is the foundation module and must not depend on higher-level modules")
                .check(allClasses);
    }

    // ── Broker API layer ─────────────────────────────────────────

    @Test
    void brokerApiModuleMustNotDependOnBrokerImplementations() {
        noClasses()
                .that().resideInAPackage("com.tradej.broker.api..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.broker.dhan..",
                        "com.tradej.broker.upstox.."
                )
                .allowEmptyShould(true)
                .because("broker-api defines the port; implementations must depend on the api, not the other way")
                .check(allClasses);
    }

    @Test
    void brokerApiModuleMustNotDependOnHigherLevelModules() {
        noClasses()
                .that().resideInAPackage("com.tradej.broker.api..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.execution..",
                        "com.tradej.strategy..",
                        "com.tradej.disruptor..",
                        "com.tradej.persistence..",
                        "com.tradej.hotpath..",
                        "com.tradej.feature..",
                        "com.tradej.gateway..",
                        "com.tradej.simulation..",
                        "com.tradej.cli..",
                        "com.tradej.app.."
                )
                .allowEmptyShould(true)
                .because("broker-api defines the port interface and must stay isolated from runtime concerns")
                .check(allClasses);
    }

    /**
     * NOTE: Rules below this point check packages that may not be on the classpath
     * when this test runs from :core:test task (only com.tradej.core.* classes
     * are on the classpath). They use {@code allowEmptyShould(true)} to pass gracefully
     * when the packages aren't loaded.
     *
     * <p>To enforce the full set of module boundary rules, run these tests from a
     * module that depends on all subprojects (e.g., trade-app).
     */

    // ── Broker implementations ───────────────────────────────────

    @Test
    void brokerImplementationsMustNotDependOnEachOther() {
        noClasses()
                .that().resideInAPackage("com.tradej.broker.dhan..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.tradej.broker.upstox..")
                .allowEmptyShould(true)
                .because("broker implementations are independent and must not cross-import")
                .check(allClasses);

        noClasses()
                .that().resideInAPackage("com.tradej.broker.upstox..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.tradej.broker.dhan..")
                .allowEmptyShould(true)
                .because("broker implementations are independent and must not cross-import")
                .check(allClasses);
    }

    // ── Simulation ───────────────────────────────────────────────

    @Test
    void simulationModuleMustNotDependOnApplicationLayer() {
        noClasses()
                .that().resideInAPackage("com.tradej.simulation..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.app..",
                        "com.tradej.cli.."
                )
                .allowEmptyShould(true)
                .because("simulation is a domain-level concern; must not depend on Spring or CLI")
                .check(allClasses);
    }

    // ── Gateway ──────────────────────────────────────────────────

    @Test
    void gatewayModuleMustNotDependOnApplicationLayer() {
        noClasses()
                .that().resideInAPackage("com.tradej.gateway..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.app..",
                        "com.tradej.cli.."
                )
                .allowEmptyShould(true)
                .because("gateway is a transport-level concern; must not depend on Spring or CLI")
                .check(allClasses);
    }

    // ── Execution layer ──────────────────────────────────────────

    @Test
    void executionModuleMustNotDependOnBrokerImplementations() {
        noClasses()
                .that().resideInAPackage("com.tradej.execution..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.broker.dhan..",
                        "com.tradej.broker.upstox.."
                )
                .allowEmptyShould(true)
                .because("execution goes through broker-api ports; must not import broker-specific classes directly")
                .check(allClasses);
    }

    @Test
    void executionModuleMustNotDependOnApplicationLayer() {
        noClasses()
                .that().resideInAPackage("com.tradej.execution..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.app..",
                        "com.tradej.cli.."
                )
                .allowEmptyShould(true)
                .because("execution is a domain-level concern; must not depend on Spring or CLI")
                .check(allClasses);
    }

    // ── Strategy layer ───────────────────────────────────────────

    @Test
    void strategyModuleMustNotDependOnApplicationLayer() {
        noClasses()
                .that().resideInAPackage("com.tradej.strategy..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.app..",
                        "com.tradej.cli.."
                )
                .allowEmptyShould(true)
                .because("strategy is a domain-level concern; must not depend on Spring or CLI")
                .check(allClasses);
    }

    // ── Disruptor event bus ──────────────────────────────────────

    @Test
    void disruptorModuleMustNotDependOnApplicationLayer() {
        noClasses()
                .that().resideInAPackage("com.tradej.disruptor..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.app..",
                        "com.tradej.cli.."
                )
                .allowEmptyShould(true)
                .because("disruptor is a transport-level concern; must not depend on Spring or CLI")
                .check(allClasses);
    }

    // ── Hotpath (pipeline) layer ─────────────────────────────────

    @Test
    void hotpathModuleMustNotDependOnApplicationLayer() {
        noClasses()
                .that().resideInAPackage("com.tradej.hotpath..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.app..",
                        "com.tradej.cli.."
                )
                .allowEmptyShould(true)
                .because("hotpath is a pipeline-level concern; must not depend on Spring or CLI")
                .check(allClasses);
    }

    // ── Persistence layer ────────────────────────────────────────

    @Test
    void persistenceModuleMustNotDependOnBrokerImplementations() {
        noClasses()
                .that().resideInAPackage("com.tradej.persistence..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.broker.dhan..",
                        "com.tradej.broker.upstox.."
                )
                .allowEmptyShould(true)
                .because("persistence is storage-level concern; must not depend on broker implementations")
                .check(allClasses);
    }

    @Test
    void persistenceModuleMustNotDependOnApplicationLayer() {
        noClasses()
                .that().resideInAPackage("com.tradej.persistence..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.tradej.app..",
                        "com.tradej.cli.."
                )
                .allowEmptyShould(true)
                .because("persistence is a storage-level concern; must not depend on Spring or CLI")
                .check(allClasses);
    }
}
