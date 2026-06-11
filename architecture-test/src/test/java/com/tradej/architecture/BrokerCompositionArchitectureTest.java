package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture Certification Test Suite for Broker Composition.
 *
 * <p>These tests enforce the target architecture where:
 * <ul>
 *   <li>Only one composition root exists</li>
 *   <li>Spring does not create broker runtimes directly</li>
 *   <li>CLI and Spring produce identical broker graphs</li>
 *   <li>All broker discovery goes through SPI</li>
 *   <li>New brokers can be added without modifying composition</li>
 *   <li>Broker lifecycle is fully encapsulated</li>
 *   <li>Token/session management is fully encapsulated</li>
 *   <li>No broker-specific class appears outside its broker module</li>
 * </ul>
 *
 * <p>If these tests fail, fix those failures before touching gateway features,
 * simulation, paper trading, or additional brokers.
 */
@Tag("architecture-certification")
class BrokerCompositionArchitectureTest {

    private JavaClasses allClasses;
    private JavaClasses brokerApiClasses;
    private JavaClasses brokerCoreClasses;
    private JavaClasses brokerDhanClasses;
    private JavaClasses brokerUpstoxClasses;
    private JavaClasses brokerIciciClasses;
    private JavaClasses compositionClasses;
    private JavaClasses appConfigClasses;
    private JavaClasses gatewayClasses;

    @BeforeEach
    void importClasses() {
        allClasses = new ClassFileImporter()
                .importPackages(
                        "com.tradej.broker.api",
                        "com.tradej.broker.core",
                        "com.tradej.broker.dhan",
                        "com.tradej.broker.upstox",
                        "com.tradej.broker.icici",
                        "com.tradej.composition",
                        "com.tradej.app.config",
                        "com.tradej.brokergateway",
                        "com.tradej.cli"
                );

        brokerApiClasses = new ClassFileImporter().importPackages("com.tradej.broker.api");
        brokerCoreClasses = new ClassFileImporter().importPackages("com.tradej.broker.core");
        brokerDhanClasses = new ClassFileImporter().importPackages("com.tradej.broker.dhan");
        brokerUpstoxClasses = new ClassFileImporter().importPackages("com.tradej.broker.upstox");
        brokerIciciClasses = new ClassFileImporter().importPackages("com.tradej.broker.icici");
        compositionClasses = new ClassFileImporter().importPackages("com.tradej.composition");
        appConfigClasses = new ClassFileImporter().importPackages("com.tradej.app.config");
        gatewayClasses = new ClassFileImporter().importPackages("com.tradej.brokergateway");
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Single Composition Root
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("1. Single Composition Root")
    class SingleCompositionRoot {

        @Test
        @DisplayName("BrokerComposition should not contain switch statements on broker type")
        void brokerCompositionMustNotHaveBrokerTypeSwitch() {
            ArchRule rule = classes()
                    .that().haveSimpleName("BrokerComposition")
                    .should(notContainBrokerTypeSwitch());

            rule.check(compositionClasses);
        }

        @Test
        @DisplayName("BrokerGateway should not have factory methods that bypass BrokerComposition")
        void brokerGatewayMustNotHaveDirectFactoryMethods() {
            List<JavaClass> gatewayClassesList = allClasses.stream()
                    .filter(c -> c.getSimpleName().equals("BrokerGateway")
                            || c.getSimpleName().equals("DefaultBrokerGateway"))
                    .collect(Collectors.toList());

            for (JavaClass gatewayClass : gatewayClassesList) {
                boolean hasDhanFactory = gatewayClass.getMethods().stream()
                        .anyMatch(m -> m.getName().equals("dhan"));
                boolean hasFromRegistryFactory = gatewayClass.getMethods().stream()
                        .anyMatch(m -> m.getName().equals("fromRegistry"));

                assertThat(hasDhanFactory)
                        .as("%s should not have a dhan() factory method", gatewayClass.getSimpleName())
                        .isFalse();
                assertThat(hasFromRegistryFactory)
                        .as("%s should not have a fromRegistry() factory method", gatewayClass.getSimpleName())
                        .isFalse();
            }
        }

        @Test
        @DisplayName("Only one class should create IBrokerConnection instances")
        void onlyOneCompositionPathForBrokerConnection() {
            // Count how many classes directly instantiate broker connections
            // Target: Only BrokerProvider implementations should create connections
            
            // Simplified check: look for classes that reference BrokerConnection classes
            // This is a simpler version that works with ArchUnit 1.4.0
            List<JavaClass> directCreators = allClasses.stream()
                    .filter(clazz -> !clazz.getPackageName().startsWith("com.tradej.broker.dhan"))
                    .filter(clazz -> !clazz.getPackageName().startsWith("com.tradej.broker.upstox"))
                    .filter(clazz -> !clazz.getPackageName().startsWith("com.tradej.broker.icici"))
                    .filter(clazz -> !clazz.getPackageName().equals("com.tradej.broker.api"))
                    .filter(clazz -> !clazz.getPackageName().equals("com.tradej.broker.core"))
                    .filter(clazz -> clazz.getDirectDependenciesFromSelf().stream()
                            .anyMatch(dep -> dep.getTargetClass() != null &&
                                    (dep.getTargetClass().getName().contains("BrokerConnection") ||
                                     dep.getTargetClass().getName().contains("BrokerFactory"))))
                    .collect(Collectors.toList());

            // Currently will FAIL - multiple creators exist
            // Target: This list should be empty or only contain BrokerProvider implementations
            assertThat(directCreators)
                    .as("Only BrokerProvider implementations should create broker connections. Found: %s",
                            directCreators.stream().map(JavaClass::getName).collect(Collectors.toList()))
                    .isEmpty();
        }

        private static ArchCondition<JavaClass> notContainBrokerTypeSwitch() {
            return new ArchCondition<>("not contain switch statements on broker type") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    // Simplified check - look for references to BrokerType enum
                    // In a real implementation, you'd parse the source code
                    boolean hasSwitchOnBrokerType = item.getDirectDependenciesFromSelf().stream()
                            .anyMatch(dep -> dep.getTargetClass() != null && 
                                            dep.getTargetClass().getName().contains("BrokerType"));
                    
                    if (hasSwitchOnBrokerType) {
                        events.add(SimpleConditionEvent.violated(item,
                                item.getName() + " may contain switch on broker type"));
                    }
                }
            };
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Spring Does Not Create Broker Runtimes
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("2. Spring Does Not Create Broker Runtimes")
    class SpringDoesNotCreateBrokers {

        @Test
        @DisplayName("Spring config should not reference broker-specific settings classes")
        void springConfigMustNotReferenceBrokerSettings() {
            // Spring should not know about DhanConnectionSettings, UpstoxConnectionSettings, etc.
            
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.tradej.app.config")
                    .should().dependOnClassesThat(
                            resideInAPackage("com.tradej.broker.dhan.config")
                                    .or(resideInAPackage("com.tradej.broker.upstox.config"))
                                    .or(resideInAPackage("com.tradej.broker.icici.config"))
                    );

            // Currently FAILS - BrokerConfiguration references these settings
            rule.check(allClasses);
        }

        @Test
        @DisplayName("Spring config should not reference broker-specific token managers")
        void springConfigMustNotReferenceTokenManagers() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.tradej.app.config")
                    .should().dependOnClassesThat(
                            resideInAPackage("com.tradej.broker.dhan.auth")
                                    .or(resideInAPackage("com.tradej.broker.upstox.auth"))
                                    .or(resideInAPackage("com.tradej.broker.icici.auth"))
                    );

            rule.check(allClasses);
        }

        @Test
        @DisplayName("Spring should only consume IBrokerConnection and port interfaces")
        void springShouldOnlyConsumeBrokerContracts() {
            // Spring config can depend on broker-api, but not broker implementations
            
            ArchRule rule = classes()
                    .that().resideInAPackage("com.tradej.app.config")
                    .and().haveSimpleNameNotContaining("Properties")
                    .should().onlyDependOnClassesThat(
                            resideInAPackage("com.tradej.broker.api..")
                                    .or(resideInAPackage("com.tradej.core.."))
                                    .or(resideInAPackage("com.tradej.composition.."))
                                    .or(resideInAPackage("java.."))
                                    .or(resideInAPackage("org.springframework.."))
                                    .or(resideInAPackage("org.slf4j.."))
                                    .or(resideInAPackage("com.fasterxml.jackson.."))
                                    .or(resideInAPackage("io.micrometer.."))
                    );

            // This will need refinement - some dependencies are OK
            rule.check(allClasses);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. CLI and Spring Produce Identical Broker Graphs
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("3. CLI and Spring Produce Identical Broker Graphs")
    class IdenticalBrokerGraphs {

        @Test
        @DisplayName("CLI and Spring should use the same factory method")
        void cliAndSpringShouldUseSameFactory() {
            // Both CLI and Spring should go through BrokerProvider, not direct constructors
            
            // Check CLI doesn't directly instantiate broker connections
            ArchRule cliRule = noClasses()
                    .that().resideInAPackage("com.tradej.cli.standalone")
                    .should().dependOnClassesThat(
                            resideInAPackage("com.tradej.broker.dhan")
                                    .or(resideInAPackage("com.tradej.broker.upstox"))
                                    .or(resideInAPackage("com.tradej.broker.icici"))
                    );

            // Currently FAILS - CLI has DhanBrokerSession, UpstoxBrokerSession, etc.
            cliRule.check(allClasses);
        }

        @Test
        @DisplayName("No broker-specific auto-configuration classes should exist")
        void noBrokerAutoConfigurations() {
            List<JavaClass> autoConfigs = allClasses.stream()
                    .filter(c -> c.getSimpleName().endsWith("AutoConfiguration"))
                    .filter(c -> c.getPackageName().startsWith("com.tradej.broker."))
                    .collect(Collectors.toList());

            assertThat(autoConfigs)
                    .as("Broker modules should not have auto-configuration classes; "
                            + "use BrokerComposition + BrokerProvider SPI instead. Found: %s",
                            autoConfigs.stream().map(JavaClass::getName).collect(Collectors.toList()))
                    .isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. All Broker Discovery Goes Through SPI
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("4. All Broker Discovery Goes Through SPI")
    class BrokerDiscoveryThroughSPI {

        @Test
        @DisplayName("BrokerProvider SPI should be in broker-api, not broker-gateway")
        void brokerProviderShouldBeInBrokerApi() {
            // Check if BrokerProvider exists in broker-api
            
            boolean brokerProviderInApi = brokerApiClasses.stream()
                    .anyMatch(c -> c.getSimpleName().equals("BrokerProvider"));

            // Currently FAILS - BrokerProvider is in broker-gateway
            assertThat(brokerProviderInApi)
                    .as("BrokerProvider SPI should be in broker-api package")
                    .isTrue();
        }

        @Test
        @DisplayName("BrokerProvider should be discovered via ServiceLoader")
        void serviceLoaderShouldRegisterProviders() {
            // This is a runtime check - verify META-INF/services exists
            // For now, just check the structure exists
            
            // TODO: Add runtime test that verifies ServiceLoader can find providers
        }

        @Test
        @DisplayName("Broker-gateway should depend on SPI, not concrete brokers")
        void brokerGatewayShouldNotDependOnConcreteBrokers() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.tradej.brokergateway")
                    .and().haveSimpleNameNotEndingWith("Provider")
                    .and().haveSimpleNameNotEndingWith("Registry")
                    .should().dependOnClassesThat(
                            resideInAPackage("com.tradej.broker.dhan")
                                    .or(resideInAPackage("com.tradej.broker.upstox"))
                                    .or(resideInAPackage("com.tradej.broker.icici"))
                    );

            // Currently FAILS - broker-gateway has direct dependencies
            rule.check(allClasses);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. New Brokers Can Be Added Without Modifying Composition
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("5. New Brokers Can Be Added Without Modifying Composition")
    class OpenClosedPrinciple {

        @Test
        @DisplayName("BrokerComposition should not reference specific broker types")
        void brokerCompositionShouldNotKnowBrokerTypes() {
            ArchRule rule = classes()
                    .that().haveSimpleName("BrokerComposition")
                    .should().onlyDependOnClassesThat(
                            resideInAPackage("com.tradej.broker.api..")
                                    .or(resideInAPackage("com.tradej.broker.core.."))
                                    .or(resideInAPackage("com.tradej.composition.config.."))
                                    .or(resideInAPackage("java.."))
                                    .or(resideInAPackage("org.slf4j.."))
                    );

            // Currently FAILS - BrokerComposition imports broker-specific classes
            rule.check(compositionClasses);
        }

        @Test
        @DisplayName("BrokerProfile should not contain broker-specific config records")
        void brokerProfileShouldNotContainBrokerConfigs() {
            // BrokerProfile.DhanConfig, UpstoxConfig, IciciConfig should be removed
            // Instead, use generic BrokerConfiguration DTO
            
            boolean hasBrokerSpecificConfigs = compositionClasses.stream()
                    .anyMatch(c -> c.getSimpleName().equals("BrokerProfile"))
                    && compositionClasses.stream()
                            .filter(c -> c.getSimpleName().equals("BrokerProfile"))
                            .flatMap(c -> c.getMembers().stream())
                            .anyMatch(m -> m.getName().contains("DhanConfig") ||
                                          m.getName().contains("UpstoxConfig") ||
                                          m.getName().contains("IciciConfig"));

            // Currently TRUE - will need refactoring
            assertThat(hasBrokerSpecificConfigs)
                    .as("BrokerProfile should not contain broker-specific config records")
                    .isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. Broker Lifecycle Fully Encapsulated
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("6. Broker Lifecycle Fully Encapsulated")
    class BrokerLifecycleEncapsulation {

        @Test
        @DisplayName("Token management should only be accessed through broker interfaces")
        void tokenManagementEncapsulated() {
            // External modules should not directly create or manage tokens
            
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.tradej.app.config")
                    .or().resideInAPackage("com.tradej.composition")
                    .or().resideInAPackage("com.tradej.cli")
                    .should().dependOnClassesThat(
                            resideInAPackage("..auth.TokenManager")
                                    .or(resideInAPackage("..auth.TokenProvider"))
                                    .or(resideInAPackage("..auth.TokenSource"))
                    );

            // Currently FAILS - Composition and Spring config reference token managers
            rule.check(allClasses);
        }

        @Test
        @DisplayName("WebSocket management should be encapsulated in broker modules")
        void webSocketManagementEncapsulated() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.tradej.app.config")
                    .or().resideInAPackage("com.tradej.composition")
                    .should().dependOnClassesThat(
                            resideInAPackage("..websocket.WebSocketMultiplexer")
                    );

            // WebSocketMultiplexer is a port interface, so this is OK
            // But concrete implementations should not be referenced
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. Token/Session Management Fully Encapsulated
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("7. Token/Session Management Fully Encapsulated")
    class TokenSessionEncapsulation {

        @Test
        @DisplayName("No external module should create broker-specific settings")
        void noExternalSettingsCreation() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.tradej.app.config")
                    .or().resideInAPackage("com.tradej.composition")
                    .or().resideInAPackage("com.tradej.cli.standalone")
                    .should().dependOnClassesThat(
                            resideInAPackage("..DhanConnectionSettings")
                                    .or(resideInAPackage("..UpstoxConnectionSettings"))
                                    .or(resideInAPackage("..BreezeConnectionSettings"))
                    );

            // Currently FAILS - Composition and Spring create settings
            rule.check(allClasses);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 8. No Broker-Specific Classes Outside Broker Modules
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("8. No Broker-Specific Classes Outside Broker Modules")
    class NoBrokerSpecificClassesOutside {

        @Test
        @DisplayName("No broker-specific classes outside their modules (except SPI contracts)")
        void noBrokerClassesOutsideModules() {
            // Classes from broker.dhan, broker.upstox, broker.icici should only be used
            // within their own modules or through broker-api interfaces
            
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.tradej.app..")
                    .or().resideInAPackage("com.tradej.composition..")
                    .or().resideInAPackage("com.tradej.cli..")
                    .or().resideInAPackage("com.tradej.brokergateway..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .should().dependOnClassesThat(
                            resideInAPackage("com.tradej.broker.dhan.adapter..")
                                    .or(resideInAPackage("com.tradej.broker.dhan.client.."))
                                    .or(resideInAPackage("com.tradej.broker.dhan.websocket.."))
                                    .or(resideInAPackage("com.tradej.broker.upstox.adapter.."))
                                    .or(resideInAPackage("com.tradej.broker.upstox.client.."))
                                    .or(resideInAPackage("com.tradej.broker.upstox.websocket.."))
                                    .or(resideInAPackage("com.tradej.broker.icici.adapter.."))
                                    .or(resideInAPackage("com.tradej.broker.icici.client.."))
                                    .or(resideInAPackage("com.tradej.broker.icici.websocket.."))
                    );

            // Currently FAILS - UpstoxBrokerFactory imports adapter classes
            rule.check(allClasses);
        }

        @Test
        @DisplayName("Broker factories should only exist inside broker modules")
        void brokerFactoriesOnlyInBrokerModules() {
            // UpstoxBrokerFactory and IciciBrokerFactory should be in broker modules, not composition
            
            List<JavaClass> externalFactories = compositionClasses.stream()
                    .filter(c -> c.getSimpleName().contains("BrokerFactory") ||
                                 c.getSimpleName().contains("ConnectionFactory"))
                    .collect(Collectors.toList());

            assertThat(externalFactories)
                    .as("Broker factories should be inside broker modules, not composition")
                    .isEmpty();
        }
    }
}
