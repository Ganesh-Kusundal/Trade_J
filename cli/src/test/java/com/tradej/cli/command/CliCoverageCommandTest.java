package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliCoverageCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void coverageCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("coverage"),
                "coverage command should be registered");
    }

    @Test
    void coverageCommand_hasDescription() {
        var covCmd = cmd.getSubcommands().get("coverage");
        assertNotNull(covCmd);
        String[] desc = covCmd.getCommandSpec().usageMessage().description();
        assertTrue(desc.length > 0);
        assertTrue(desc[0].contains("Coverage") || desc[0].contains("coverage"));
    }

    @Test
    void readinessCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("readiness"),
                "readiness command should be registered");
    }
}
