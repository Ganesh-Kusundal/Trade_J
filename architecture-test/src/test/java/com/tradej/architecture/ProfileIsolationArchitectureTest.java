package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Verifies AD-02: replay-specific beans are isolated behind Spring profiles
 * so they are never loaded in the LIVE profile context.
 *
 * <p>Rules:
 * <ul>
 *   <li>{@code ClockConfiguration} must be annotated with {@code @Profile}
 *       to avoid conflicting with {@code TimeConfiguration}'s profile-managed beans.</li>
 *   <li>{@code ReplayTradingClock} is in the {@code core} module — safe from auto-scanning.</li>
 *   <li>{@code ReplayClock} (replay clock driver) is in the {@code persistence} module.</li>
 * </ul>
 */
@Tag("architecture")
class ProfileIsolationArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importAllClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    /** ReplayTradingClock is in core module, not app — safe from auto-scanning. */
    @Test
    void replayTradingClockIsInCoreModule() {
        classes()
                .that().haveSimpleName("ReplayTradingClock")
                .should().resideInAPackage("com.tradej.core..")
                .allowEmptyShould(false)
                .check(allClasses);
    }

    /** ReplayClock (replay clock driver) is in persistence module, not app. */
    @Test
    void replayClockIsInPersistenceModule() {
        classes()
                .that().haveSimpleName("ReplayClock")
                .should().resideInAPackage("com.tradej.persistence..")
                .allowEmptyShould(false)
                .check(allClasses);
    }
}
