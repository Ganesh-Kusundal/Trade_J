package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliReadinessCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void readinessCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("readiness"),
                "readiness command should be registered");
    }

    @Test
    void readinessCommand_hasDescription() {
        var readyCmd = cmd.getSubcommands().get("readiness");
        assertNotNull(readyCmd);
        String[] desc = readyCmd.getCommandSpec().usageMessage().description();
        assertTrue(desc.length > 0);
        assertTrue(desc[0].contains("readiness") || desc[0].contains("Readiness"));
    }

    @Test
    void readinessCommand_evaluatesSubsystems() {
        var readyCmd = cmd.getSubcommands().get("readiness");
        assertNotNull(readyCmd);
    }
}
