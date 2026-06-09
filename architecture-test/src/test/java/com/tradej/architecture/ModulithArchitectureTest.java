package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring Modulith-style architecture boundary verification tests.
 * <p>
 * Defines the expected module dependency graph and enforces:
 * <ul>
 *   <li>Each module only depends on its declared dependencies</li>
 *   <li>Broker adapters are isolated from each other</li>
 *   <li>The module dependency graph is acyclic</li>
 *   <li>Core has no external framework dependencies (reinforces SpringFreeArchitectureTest)</li>
 * </ul>
 */
@Tag("architecture")
class ModulithArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importAllClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    // ── Module definitions ──

    private static final Module CORE = new Module(
            "core",
            Set.of("com.tradej.core.."),
            Set.of()
    );

    private static final Module BROKER_API = new Module(
            "broker-api",
            Set.of("com.tradej.broker.api.."),
            Set.of("core")
    );

    private static final Module BROKER_CORE = new Module(
            "broker-core",
            Set.of("com.tradej.broker.core.."),
            Set.of("broker-api", "core")
    );

    private static final Module BROKER_DHAN = new Module(
            "broker-dhan",
            Set.of("com.tradej.broker.dhan.."),
            Set.of("core", "broker-api", "broker-core")
    );

    private static final Module BROKER_UPSTOX = new Module(
            "broker-upstox",
            Set.of("com.tradej.broker.upstox.."),
            Set.of("core", "broker-api", "broker-core")
    );

    private static final Module BROKER_ICICI = new Module(
            "broker-icici",
            Set.of("com.tradej.broker.icici.."),
            Set.of("core", "broker-api", "broker-core")
    );

    private static final Module TRADING_EXECUTION = new Module(
            "trading-execution",
            Set.of("com.tradej.execution.."),
            Set.of("core", "broker-api", "broker-core", "trading-strategy", "data-persistence")
    );

    private static final Module TRADING_STRATEGY = new Module(
            "trading-strategy",
            Set.of("com.tradej.strategy.."),
            Set.of("core")
    );

    private static final Module DATA_PERSISTENCE = new Module(
            "data-persistence",
            Set.of("com.tradej.persistence.."),
            Set.of("core", "pipeline-core")
    );

    private static final Module PIPELINE_CORE = new Module(
            "pipeline-core",
            Set.of(
                    "com.tradej.pipeline.graph..",
                    "com.tradej.pipeline.clock..",
                    "com.tradej.pipeline.compiler..",
                    "com.tradej.pipeline.state..",
                    "com.tradej.pipeline.registry..",
                    "com.tradej.pipeline.reactor.."
            ),
            Set.of("core")
    );

    private static final List<Module> ALL_MODULES = List.of(
            CORE, BROKER_API, BROKER_CORE,
            BROKER_DHAN, BROKER_UPSTOX, BROKER_ICICI,
            TRADING_EXECUTION, TRADING_STRATEGY,
            DATA_PERSISTENCE, PIPELINE_CORE
    );

    // ── Module boundary verification ──

    @Test
    void coreMustNotDependOnAnyModule() {
        assertModuleDependsOnlyOn(CORE, Set.of());
    }

    @Test
    void brokerApiMustOnlyDependOnCore() {
        assertModuleDependsOnlyOn(BROKER_API, Set.of("core"));
    }

    @Test
    void brokerCoreMustDependOnBrokerApiAndCore() {
        assertModuleDependsOnlyOn(BROKER_CORE, Set.of("broker-api", "core"));
    }

    @Test
    void brokerDhanMustOnlyDependOnCoreBrokerApiAndBrokerCore() {
        assertModuleDependsOnlyOn(BROKER_DHAN, Set.of("core", "broker-api", "broker-core"));
    }

    @Test
    void brokerUpstoxMustOnlyDependOnCoreBrokerApiAndBrokerCore() {
        assertModuleDependsOnlyOn(BROKER_UPSTOX, Set.of("core", "broker-api", "broker-core"));
    }

    @Test
    void brokerIciciMustOnlyDependOnCoreBrokerApiAndBrokerCore() {
        assertModuleDependsOnlyOn(BROKER_ICICI, Set.of("core", "broker-api", "broker-core"));
    }

    @Test
    void tradingExecutionMustDependOnCoreBrokerApiBrokerCoreTradingStrategyAndDataPersistence() {
        assertModuleDependsOnlyOn(TRADING_EXECUTION, Set.of("core", "broker-api", "broker-core", "trading-strategy", "data-persistence"));
    }

    @Test
    void tradingStrategyMustOnlyDependOnCore() {
        assertModuleDependsOnlyOn(TRADING_STRATEGY, Set.of("core"));
    }

    @Test
    void dataPersistenceMustDependOnCoreAndPipelineCore() {
        assertModuleDependsOnlyOn(DATA_PERSISTENCE, Set.of("core", "pipeline-core"));
    }

    @Test
    void pipelineCoreMustOnlyDependOnCore() {
        assertModuleDependsOnlyOn(PIPELINE_CORE, Set.of("core"));
    }

    // ── Broker isolation ──

    @Test
    void brokerModulesMustNotDependOnEachOther() {
        List<Module> brokerAdapters = List.of(BROKER_DHAN, BROKER_UPSTOX, BROKER_ICICI);
        List<String> violations = new ArrayList<>();

        for (Module adapter : brokerAdapters) {
            for (String packageName : adapter.packages) {
                for (Module other : brokerAdapters) {
                    if (adapter == other) {
                        continue;
                    }
                    String forbiddenPattern = packageName.replace("..", "");
                    noClasses()
                            .that().resideInAPackage(packageName)
                            .should().dependOnClassesThat().resideInAnyPackage(
                                    other.packages.stream()
                                            .map(p -> p.replace("..", "") + "..")
                                            .toArray(String[]::new)
                            )
                            .as(String.format("%s must not depend on %s", adapter.name, other.name))
                            .allowEmptyShould(true)
                            .check(allClasses);
                }
            }
        }
    }

    // ── Core framework independence (reinforces SpringFreeArchitectureTest) ──

    @Test
    void coreModuleMustNotDependOnSpringFramework() {
        noClasses()
                .that().resideInAPackage("com.tradej.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.annotation..",
                        "jakarta.validation.."
                )
                .because("Core module must be framework-agnostic")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    void brokerApiModuleMustNotDependOnSpringFramework() {
        noClasses()
                .that().resideInAPackage("com.tradej.broker.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.annotation..",
                        "jakarta.validation.."
                )
                .because("Broker API must be framework-agnostic")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    void brokerCoreModuleMustNotDependOnSpringFramework() {
        noClasses()
                .that().resideInAPackage("com.tradej.broker.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.annotation..",
                        "jakarta.validation.."
                )
                .because("Broker Core must be framework-agnostic")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    void tradingStrategyModuleMustNotDependOnSpringFramework() {
        noClasses()
                .that().resideInAPackage("com.tradej.strategy..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.annotation..",
                        "jakarta.validation.."
                )
                .because("Trading Strategy must be framework-agnostic")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // ── Cycle detection ──

    @Test
    void moduleDependencyGraphMustNotHaveCycles() {
        List<String> cycles = findCycles(ALL_MODULES);
        assertTrue(cycles.isEmpty(),
                "Module dependency graph contains cycles:\n"
                        + String.join("\n", cycles));
    }

    // ── Helper methods ──

    private static void assertModuleDependsOnlyOn(Module module, Set<String> allowedDependencies) {
        List<String> violations = new ArrayList<>();

        for (String packageName : module.packages) {
            for (Module otherModule : ALL_MODULES) {
                if (otherModule == module) {
                    continue;
                }
                if (allowedDependencies.contains(otherModule.name)) {
                    continue;
                }
                String[] forbiddenPackages = otherModule.packages.stream()
                        .map(p -> p.replace("..", "") + "..")
                        .toArray(String[]::new);

                noClasses()
                        .that().resideInAPackage(packageName)
                        .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                        .as(String.format("%s must not depend on %s", module.name, otherModule.name))
                        .allowEmptyShould(true)
                        .check(allClasses);
            }
        }
    }

    private static List<String> findCycles(List<Module> modules) {
        List<String> cycles = new ArrayList<>();

        for (Module module : modules) {
            Set<String> visited = new java.util.HashSet<>();
            List<String> path = new ArrayList<>();
            findCyclesDfs(module, modules, visited, path, cycles);
        }

        return cycles;
    }

    private static void findCyclesDfs(
            Module current,
            List<Module> allModules,
            Set<String> visited,
            List<String> path,
            List<String> cycles
    ) {
        if (visited.contains(current.name)) {
            int cycleStart = path.indexOf(current.name);
            if (cycleStart >= 0) {
                List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                cycle.add(current.name);
                cycles.add("Cycle: " + String.join(" -> ", cycle));
            }
            return;
        }

        visited.add(current.name);
        path.add(current.name);

        for (String depName : current.dependencies) {
            allModules.stream()
                    .filter(m -> m.name.equals(depName))
                    .findFirst()
                    .ifPresent(dep -> findCyclesDfs(dep, allModules, visited, path, cycles));
        }

        path.remove(path.size() - 1);
        visited.remove(current.name);
    }

    // ── Module definition ──

    private static final class Module {
        final String name;
        final Set<String> packages;
        final Set<String> dependencies;

        Module(String name, Set<String> packages, Set<String> dependencies) {
            this.name = name;
            this.packages = packages;
            this.dependencies = dependencies;
        }
    }
}
