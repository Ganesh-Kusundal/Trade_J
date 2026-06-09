package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces mandatory design patterns across the Trade-J codebase:
 * <ul>
 *   <li>OMS must use the Command Pattern (TradingCommand + CommandHandler)</li>
 *   <li>Risk must use Chain of Responsibility (RiskCheck + RiskCheckChain)</li>
 *   <li>Indicators must use Strategy Pattern (IndicatorProvider + IndicatorRegistry)</li>
 *   <li>Scanner must use Specification Pattern (ScanCriterion + ScanCriterionRegistry)</li>
 *   <li>OMS must use State Pattern (OrderStateMachine + LifecycleState)</li>
 *   <li>Broker adapters must use Adapter Pattern (IBrokerConnection implementations)</li>
 *   <li>Plugin discovery must use SPI (ServiceLoader)</li>
 * </ul>
 */
@Tag("architecture")
class DesignPatternArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importAllClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    // ── Command Pattern ──

    @Test
    void commandPatternClassesExistInExecutionModule() {
        classes()
                .that().haveSimpleName("TradingCommand")
                .should().resideInAPackage("com.tradej.execution.command..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void commandHandlerExistsInExecutionModule() {
        classes()
                .that().haveSimpleName("CommandHandler")
                .should().resideInAPackage("com.tradej.execution.command..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void commandResultExistsInExecutionModule() {
        classes()
                .that().haveSimpleName("CommandResult")
                .should().resideInAPackage("com.tradej.execution.command..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    // ── Chain of Responsibility ──

    @Test
    void riskCheckInterfaceExists() {
        classes()
                .that().haveSimpleName("RiskCheck")
                .should().resideInAPackage("com.tradej.execution.risk..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void riskCheckChainExists() {
        classes()
                .that().haveSimpleName("RiskCheckChain")
                .should().resideInAPackage("com.tradej.execution.risk..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void riskVerdictExists() {
        classes()
                .that().haveSimpleName("RiskVerdict")
                .should().resideInAPackage("com.tradej.execution.risk..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    // ── State Pattern ──

    @Test
    void orderStateMachineExistsInCoreOms() {
        classes()
                .that().haveSimpleName("OrderStateMachine")
                .should().resideInAPackage("com.tradej.core.domain.oms..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void lifecycleStateExistsInCoreOms() {
        classes()
                .that().haveSimpleName("LifecycleState")
                .should().resideInAPackage("com.tradej.core.domain.oms..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    // ── Strategy Pattern ──

    @Test
    void indicatorProviderSpiExists() {
        classes()
                .that().haveSimpleName("IndicatorProvider")
                .should().resideInAPackage("com.tradej.indicators.spi..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void indicatorRegistryExists() {
        classes()
                .that().haveSimpleName("IndicatorRegistry")
                .should().resideInAPackage("com.tradej.indicators..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    // ── Specification Pattern ──

    @Test
    void scanCriterionInterfaceExists() {
        classes()
                .that().haveSimpleName("ScanCriterion")
                .should().resideInAPackage("com.tradej.scanner.criterion..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void scanCriterionRegistryExists() {
        classes()
                .that().haveSimpleName("ScanCriterionRegistry")
                .should().resideInAPackage("com.tradej.scanner.criterion..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    // ── Registry Pattern ──

    @Test
    void brokerPluginRegistryExists() {
        classes()
                .that().haveSimpleName("BrokerPluginRegistry")
                .should().resideInAPackage("com.tradej.brokergateway.spi..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    // ── Adapter Pattern ──

    @Test
    void brokerConnectionInterfaceExistsInBrokerApi() {
        classes()
                .that().haveSimpleName("IBrokerConnection")
                .should().resideInAPackage("com.tradej.broker.api..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    // ── Decorator Pattern ──

    @Test
    void observableDecoratorsExistForBrokerPorts() {
        classes()
                .that().haveSimpleNameStartingWith("Observable")
                .should().resideInAPackage("com.tradej.broker.core.observability..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    // ── Hexagonal: Domain must not know about infrastructure ──

    @Test
    void domainOmsMustNotDependOnBrokerImplementations() {
        noClasses()
                .that().resideInAPackage("com.tradej.core.domain.oms..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tradej.broker.dhan..",
                        "com.tradej.broker.upstox..",
                        "com.tradej.broker.icici.."
                )
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    void executionCommandMustNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("com.tradej.execution.command..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    void riskCheckMustNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("com.tradej.execution.risk..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // ── Usage enforcement: patterns must be wired into production ──

    @Test
    void positionRiskHandlerMustDependOnRiskCheckChain() {
        classes()
                .that().haveSimpleName("PositionRiskHandler")
                .should().dependOnClassesThat().haveSimpleName("RiskCheckChain")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void orderControllerMustDependOnOrderApplicationService() {
        classes()
                .that().haveSimpleName("OrderController")
                .should().dependOnClassesThat().haveSimpleName("OrderApplicationService")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void replayRunnerMustDependOnReplayMetrics() {
        classes()
                .that().haveSimpleName("ReplayRunner")
                .should().dependOnClassesThat().haveSimpleName("ReplayMetrics")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void matchingEngineMustDependOnSimulationMetrics() {
        classes()
                .that().haveSimpleName("MatchingEngine")
                .should().dependOnClassesThat().haveSimpleName("SimulationMetrics")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    void queryEngineMustDependOnQueryMetrics() {
        classes()
                .that().haveSimpleName("DuckDbQueryEngine")
                .should().dependOnClassesThat().haveSimpleName("QueryMetrics")
                .allowEmptyShould(false)
                .check(allClasses);
    }
}
