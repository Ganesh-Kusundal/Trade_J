package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliDataSourcesCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void dataSourcesCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("datasources"),
                "datasources command should be registered");
    }

    @Test
    void dataSourcesCommand_hasDescription() {
        var dsCmd = cmd.getSubcommands().get("datasources");
        assertNotNull(dsCmd);
        String[] desc = dsCmd.getCommandSpec().usageMessage().description();
        assertTrue(desc.length > 0);
        assertTrue(desc[0].contains("data"), "description should mention data");
    }

    @Test
    void dataSourcesCommand_knowsExpectedSources() {
        var dsCmd = cmd.getSubcommands().get("datasources");
        assertNotNull(dsCmd);
    }
}
