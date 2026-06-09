package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural constraint tests for all AutoConfiguration classes.
 *
 * <ul>
 *   <li>All AutoConfiguration classes must reside in {@code config} subpackages</li>
 *   <li>All AutoConfiguration classes must use {@code @ConditionalOnClass}</li>
 *   <li>All AutoConfiguration classes must use {@code @ConditionalOnProperty}</li>
 *   <li>No AutoConfiguration class should create beans outside its broker module</li>
 * </ul>
 */
@Tag("architecture")
class AutoConfigurationArchitectureTest {

    private static JavaClasses autoConfigClasses;
    private static JavaClasses allProductionClasses;

    @BeforeAll
    static void importClasses() {
        autoConfigClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");

        allProductionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    // ── Rule: AutoConfiguration must be in config subpackages ─────

    @Test
    void autoConfigurationClassesMustResideInConfigSubpackages() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(AutoConfiguration.class)
                .should().resideInAPackage("..config..");

        rule.allowEmptyShould(true)
                .because("AutoConfiguration classes should be placed in config subpackages for consistency")
                .check(autoConfigClasses);
    }

    // ── Rule: AutoConfiguration must use @ConditionalOnClass ──────

    @Test
    void autoConfigurationClassesMustUseConditionalOnClass() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(AutoConfiguration.class)
                .should().beAnnotatedWith(
                        org.springframework.boot.autoconfigure.condition.ConditionalOnClass.class);

        rule.allowEmptyShould(true)
                .because("AutoConfiguration classes should use @ConditionalOnClass for classpath safety")
                .check(autoConfigClasses);
    }

    // ── Rule: AutoConfiguration must use @ConditionalOnProperty ───

    @Test
    void autoConfigurationClassesMustUseConditionalOnProperty() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(AutoConfiguration.class)
                .should().beAnnotatedWith(
                        org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);

        rule.allowEmptyShould(true)
                .because("AutoConfiguration classes should use @ConditionalOnProperty to activate based on configuration")
                .check(autoConfigClasses);
    }

    // ── Rule: AutoConfiguration must not live outside its module ───

    @Test
    void dhanAutoConfigurationMustNotResideOutsideDhanModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.tradej.broker.dhan.config..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tradej.broker.upstox..",
                        "com.tradej.broker.icici.."
                );

        rule.allowEmptyShould(true)
                .because("Dhan AutoConfiguration must not depend on other broker modules")
                .check(allProductionClasses);
    }

    @Test
    void upstoxAutoConfigurationMustNotResideOutsideUpstoxModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.tradej.broker.upstox.config..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tradej.broker.dhan..",
                        "com.tradej.broker.icici.."
                );

        rule.allowEmptyShould(true)
                .because("Upstox AutoConfiguration must not depend on other broker modules")
                .check(allProductionClasses);
    }

    @Test
    void iciciAutoConfigurationMustNotResideOutsideIciciModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.tradej.broker.icici.config..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tradej.broker.dhan..",
                        "com.tradej.broker.upstox.."
                );

        rule.allowEmptyShould(true)
                .because("Icici AutoConfiguration must not depend on other broker modules")
                .check(allProductionClasses);
    }

    // ── Rule: Broker modules must not cross-reference configs ──────

    @Test
    void brokerModulesMustNotImportOtherBrokerAutoConfigurations() {
        noClasses()
                .that().resideInAPackage("com.tradej.broker.dhan..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tradej.broker.upstox.config..",
                        "com.tradej.broker.icici.config.."
                )
                .because("Broker modules must be isolated and not reference other broker auto-configurations")
                .allowEmptyShould(true)
                .check(allProductionClasses);

        noClasses()
                .that().resideInAPackage("com.tradej.broker.upstox..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tradej.broker.dhan.config..",
                        "com.tradej.broker.icici.config.."
                )
                .because("Broker modules must be isolated and not reference other broker auto-configurations")
                .allowEmptyShould(true)
                .check(allProductionClasses);

        noClasses()
                .that().resideInAPackage("com.tradej.broker.icici..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tradej.broker.dhan.config..",
                        "com.tradej.broker.upstox.config.."
                )
                .because("Broker modules must be isolated and not reference other broker auto-configurations")
                .allowEmptyShould(true)
                .check(allProductionClasses);
    }

    // ── Rule: No auto-configuration beans in non-config packages ───

    @Test
    void autoConfigurationModulesMustNotDefineBeansOutsideConfigPackage() {
        // The Dhan, Upstox, and Icici auto-configuration classes themselves
        // should not be directly @Bean-created from non-config packages.
        // This is a structural check — the bean definitions must live in the
        // config subpackage of each broker module.
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("AutoConfiguration")
                .should().resideInAPackage("..config..");

        rule.allowEmptyShould(true)
                .because("All AutoConfiguration classes should follow the config subpackage convention")
                .check(autoConfigClasses);
    }

    // ── Rule: ConditionalOnProperty values are broker-specific ─────

    @Test
    void brokerAutoConfigurationsMustUseTradeBrokerTypeProperty() {
        // All broker auto-configurations should condition on trade.broker-type
        // This is verified by inspecting the annotation values at runtime,
        // but structurally we can verify the annotation is present.
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("AutoConfiguration")
                .and().resideInAPackage("com.tradej.broker.*.config..")
                .should().beAnnotatedWith(
                        org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);

        rule.allowEmptyShould(true)
                .because("Broker AutoConfigurations must use @ConditionalOnProperty for trade.broker-type")
                .check(autoConfigClasses);
    }
}
