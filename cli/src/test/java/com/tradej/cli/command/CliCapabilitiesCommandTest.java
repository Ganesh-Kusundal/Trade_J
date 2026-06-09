package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliCapabilitiesCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void capabilitiesCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("capabilities"),
                "capabilities command should be registered");
    }

    @Test
    void capabilitiesCommand_hasDescription() {
        var capCmd = cmd.getSubcommands().get("capabilities");
        assertNotNull(capCmd);
        String[] desc = capCmd.getCommandSpec().usageMessage().description();
        assertTrue(desc.length > 0);
        assertTrue(desc[0].contains("capability") || desc[0].contains("Capability"),
                "description should mention capability");
    }

    @Test
    void helpCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("help"),
                "help command should be registered");
    }
}
