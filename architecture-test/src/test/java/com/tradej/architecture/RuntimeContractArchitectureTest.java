package com.tradej.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("architecture")
class RuntimeContractArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importAllClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tradej");
    }

    @Test
    void brokerCapabilityLookupMustGoThroughCapabilityRouter() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : allClasses) {
            if (isAllowedCapabilityLookupOwner(javaClass)) {
                continue;
            }
            for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                if (call.getTarget().getName().equals("getCapability")
                        && call.getTargetOwner().getFullName().equals("com.tradej.broker.api.IBrokerConnection")) {
                    violations.add(String.format("  %s calls IBrokerConnection.getCapability at %s",
                            javaClass.getFullName(), call.getSourceCodeLocation()));
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Broker capability lookup must go through BrokerCapabilityRouter:\n"
                        + String.join("\n", violations));
    }

    @Test
    void externalSymbolNormalizationMustGoThroughInstrumentIdentityService() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : allClasses) {
            if (isAllowedNormalizerOwner(javaClass)) {
                continue;
            }
            for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                if (call.getTarget().getName().equals("normalize")
                        && call.getTargetOwner().getFullName()
                        .equals("com.tradej.core.domain.instrument.ContractSymbolNormalizer")) {
                    violations.add(String.format("  %s calls ContractSymbolNormalizer.normalize at %s",
                            javaClass.getFullName(), call.getSourceCodeLocation()));
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "External modules must use InstrumentIdentityService/InstrumentKey.of for symbol identity:\n"
                        + String.join("\n", violations));
    }

    private static boolean isAllowedCapabilityLookupOwner(JavaClass javaClass) {
        return javaClass.getFullName().equals("com.tradej.broker.api.IBrokerConnection")
                || javaClass.getFullName().equals("com.tradej.broker.api.capability.BrokerCapabilityRouter");
    }

    private static boolean isAllowedNormalizerOwner(JavaClass javaClass) {
        String name = javaClass.getFullName();
        return name.startsWith("com.tradej.core.domain.instrument.")
                || name.startsWith("com.tradej.broker.dhan.instrument.")
                || name.startsWith("com.tradej.broker.upstox.instrument.")
                || name.startsWith("com.tradej.broker.upstox.expired.")
                || name.startsWith("com.tradej.broker.icici.instrument.");
    }
}
