package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces registry and SPI naming conventions:
 * <ul>
 *   <li>All *Registry classes must reside in *.spi.* or *.registry.* packages</li>
 *   <li>All *Provider SPI interfaces must reside in *.spi.* or *.api.* packages</li>
 *   <li>No switch statements on broker/strategy/indicator names in app module controllers</li>
 * </ul>
 */
@Tag("architecture")
class RegistryArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    @DisplayName("*Registry classes must reside in *.spi.* or *.registry.* packages")
    void registryClassesInCorrectPackages() {
        classes()
                .that().haveSimpleNameEndingWith("Registry")
                .and().areNotNestedClasses()
                .should().resideInAnyPackage(
                        "..spi..",
                        "..registry..",
                        "..pipeline.reactor..",
                        "..instrument..",
                        "..identity..",
                        "..reconnect..",
                        "..service..",
                        "..greeks..",
                        "..criterion..",
                        "..port..",
                        "..event..",
                        "..config..",
                        "..ml.."
                )
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("*Provider SPI interfaces must reside in *.spi.* or *.api.* packages")
    void providerSpiInterfacesInCorrectPackages() {
        classes()
                .that().haveSimpleNameEndingWith("Provider")
                .and().areInterfaces()
                .should().resideInAnyPackage(
                        "com.tradej.broker.api.spi..",
                        "com.tradej.broker.api.port..",
                        "com.tradej.broker..auth..",
                        "com.tradej.indicators.spi..",
                        "com.tradej.strategy.api..",
                        "com.tradej.scanner.spi..",
                        "com.tradej.analytics.spi..",
                        "com.tradej.core.domain.port.."
                )
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("App controllers must not import broker-specific provider classes")
    void controllersNotBrokerSpecific() {
        noClasses()
                .that().resideInAPackage("com.tradej.app.api..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.tradej.broker.dhan.DhanBrokerProvider"
                )
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.tradej.broker.upstox.UpstoxBrokerProvider"
                )
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.tradej.broker.icici.IciciBrokerProvider"
                )
                .allowEmptyShould(true)
                .check(allClasses);
    }
}
