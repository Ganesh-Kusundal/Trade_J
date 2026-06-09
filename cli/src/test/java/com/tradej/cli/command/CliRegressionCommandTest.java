package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliRegressionCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void regressionCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("regression"),
                "regression command should be registered");
    }

    @Test
    void regressionCommand_hasFilterOption() {
        var regCmd = cmd.getSubcommands().get("regression");
        assertNotNull(regCmd);
        var filterOption = regCmd.getCommandSpec().optionsMap().get("--filter");
        assertNotNull(filterOption, "--filter option should exist");
    }

    @Test
    void regressionCommand_hasSummaryOption() {
        var regCmd = cmd.getSubcommands().get("regression");
        assertNotNull(regCmd);
        var summaryOption = regCmd.getCommandSpec().optionsMap().get("--summary");
        assertNotNull(summaryOption, "--summary option should exist");
    }

    @Test
    void scanTestFiles_findsTests() {
        List<CliRegressionCommand.RegressionEntry> entries = CliRegressionCommand.scanTestFiles();
        assertFalse(entries.isEmpty(), "should find test files across modules");
        assertTrue(entries.size() >= 100, "should find at least 100 test classes, found: " + entries.size());

        List<String> modules = entries.stream().map(CliRegressionCommand.RegressionEntry::module).distinct().toList();
        assertTrue(modules.contains("core"), "should find core tests");
        assertTrue(modules.contains("cli"), "should find CLI tests");
        assertTrue(modules.contains("broker-gateway"), "should find broker-gateway tests");
    }

    @Test
    void categorizeTest_assignsCorrectCategories() {
        assertEquals("architecture", CliRegressionCommand.categorizeTest("SpringFreeArchitectureTest", "unit"));
        assertEquals("certification", CliRegressionCommand.categorizeTest("ReplayEndToEndCertificationTest", "unit"));
        assertEquals("concurrency", CliRegressionCommand.categorizeTest("BrokerPluginRegistryConcurrencyTest", "unit"));
        assertEquals("integration", CliRegressionCommand.categorizeTest("GatewayReplaySmokeTest", "integration"));
        assertEquals("broker", CliRegressionCommand.categorizeTest("DhanBrokerTest", "broker-rest"));
        assertEquals("unit", CliRegressionCommand.categorizeTest("RichTableTest", "unit"));
    }
}
