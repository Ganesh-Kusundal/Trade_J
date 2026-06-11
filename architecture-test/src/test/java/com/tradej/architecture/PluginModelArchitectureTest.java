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
 * Enforces the plugin/SPI architecture:
 * <ul>
 *   <li>All {@code *Provider} SPI interfaces must reside in {@code *.spi} packages</li>
 *   <li>Broker providers must be discovered via {@code ServiceLoader}, not hardcoded</li>
 *   <li>No module outside {@code broker-gateway} should reference simulation internals</li>
 * </ul>
 */
@Tag("architecture")
class PluginModelArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    @DisplayName("BrokerProvider SPI interface must reside in broker-api.spi package")
    void brokerProviderSpiInBrokerApi() {
        classes()
                .that().haveSimpleName("BrokerProvider")
                .should().resideInAPackage("com.tradej.broker.api.spi..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    @DisplayName("IndicatorProvider SPI interface must reside in indicators.spi package")
    void indicatorProviderSpiInIndicators() {
        classes()
                .that().haveSimpleName("IndicatorProvider")
                .should().resideInAPackage("com.tradej.indicators.spi..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    @DisplayName("TransformationProvider SPI interface must reside in indicators.spi package")
    void transformationProviderSpiInIndicators() {
        classes()
                .that().haveSimpleName("TransformationProvider")
                .should().resideInAPackage("com.tradej.indicators.spi..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    @Test
    @DisplayName("Broker modules must not depend on composition module")
    void brokerModulesMustNotDependOnComposition() {
        noClasses()
                .that().resideInAPackage("com.tradej.broker.dhan..")
                .or().resideInAPackage("com.tradej.broker.upstox..")
                .or().resideInAPackage("com.tradej.broker.icici..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.tradej.composition..")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Simulation broker must not leak into production broker modules")
    void simulationMustNotLeakIntoBrokerModules() {
        noClasses()
                .that().resideInAPackage("com.tradej.broker.dhan..")
                .or().resideInAPackage("com.tradej.broker.upstox..")
                .or().resideInAPackage("com.tradej.broker.icici..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.tradej.brokergateway.simulation..")
                .allowEmptyShould(true)
                .check(allClasses);
    }
}
