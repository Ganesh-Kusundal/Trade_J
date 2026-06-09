package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliArchitectureCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void architectureCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("architecture"),
                "architecture command should be registered");
    }

    @Test
    void architectureCommand_hasSubcommands() {
        var archCmd = cmd.getSubcommands().get("architecture");
        assertNotNull(archCmd);
        var subs = archCmd.getSubcommands();
        assertTrue(subs.containsKey("modules"), "should have modules subcommand");
        assertTrue(subs.containsKey("patterns"), "should have patterns subcommand");
        assertTrue(subs.containsKey("events"), "should have events subcommand");
        assertTrue(subs.containsKey("rules"), "should have rules subcommand");
    }

    @Test
    void architectureCommand_hasGraphOption() {
        var archCmd = cmd.getSubcommands().get("architecture");
        assertNotNull(archCmd);
        var graphOption = archCmd.getCommandSpec().optionsMap().get("--graph");
        assertNotNull(graphOption, "--graph option should exist");
    }

    @Test
    void architectureCommand_hasDescription() {
        var archCmd = cmd.getSubcommands().get("architecture");
        assertNotNull(archCmd);
        String[] desc = archCmd.getCommandSpec().usageMessage().description();
        assertTrue(desc.length > 0);
        assertTrue(desc[0].contains("architecture") || desc[0].contains("Architecture"),
                "description should mention architecture");
    }
}
