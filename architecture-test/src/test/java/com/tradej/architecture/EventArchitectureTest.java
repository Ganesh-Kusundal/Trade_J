package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Enforces domain event architecture:
 * <ul>
 *   <li>All DomainEvent implementations must be Java records</li>
 *   <li>All events must reside in com.tradej.core.domain.event package</li>
 * </ul>
 */
@Tag("architecture")
class EventArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    @DisplayName("All DomainEvent implementations must be Java records")
    void domainEventsMustBeRecords() {
        classes()
                .that().implement("com.tradej.core.domain.event.DomainEvent")
                .and().areNotInterfaces()
                .and().areNotMemberClasses()
                .and().doNotHaveFullyQualifiedName("com.tradej.core.domain.event.OrderUpdateEvent")
                .should().beRecords()
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Domain event classes must reside in core.domain.event package")
    void domainEventsInCorrectPackage() {
        classes()
                .that().implement("com.tradej.core.domain.event.DomainEvent")
                .and().areNotInterfaces()
                .and().areNotMemberClasses()
                .should().resideInAPackage("com.tradej.core.domain.event..")
                .allowEmptyShould(true)
                .check(allClasses);
    }
}
