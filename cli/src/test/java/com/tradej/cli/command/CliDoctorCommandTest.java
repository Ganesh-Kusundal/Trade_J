package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliDoctorCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void doctorCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("doctor"),
                "doctor command should be registered");
    }

    @Test
    void doctorCommand_hasDescription() {
        var doctorCmd = cmd.getSubcommands().get("doctor");
        assertNotNull(doctorCmd);
        String[] desc = doctorCmd.getCommandSpec().usageMessage().description();
        assertTrue(desc.length > 0, "doctor should have a description");
        assertTrue(desc[0].contains("health"), "description should mention health");
    }

    @Test
    void doctorOperations_checkJavaVersion_returnsResult() {
        var result = CliDoctorOperations.checkJavaVersion();
        assertNotNull(result);
        assertEquals("Java Version", result.name());
        assertTrue(java.util.List.of("PASS", "WARN").contains(result.status()));
        assertFalse(result.detail().isEmpty());
    }

    @Test
    void doctorOperations_checkGradleWrapper_returnsResult() {
        var result = CliDoctorOperations.checkGradleWrapper();
        assertNotNull(result);
        assertEquals("Gradle Wrapper", result.name());
    }

    @Test
    void doctorOperations_checkBrokerProviders_findsProviders() {
        var result = CliDoctorOperations.checkBrokerProviders();
        assertNotNull(result);
        assertEquals("Broker Providers", result.name());
        assertEquals("PASS", result.status());
        assertTrue(result.detail().contains("discovered"));
    }

    @Test
    void doctorOperations_runAllChecks_returnsMultipleResults() {
        var results = CliDoctorOperations.runAllChecks();
        assertFalse(results.isEmpty(), "runAllChecks should return multiple results");
        assertTrue(results.size() >= 5, "should have at least 5 checks");
    }
}
