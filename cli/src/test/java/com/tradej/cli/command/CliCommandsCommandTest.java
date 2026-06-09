package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliCommandsCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void commandsCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("commands"),
                "commands command should be registered");
    }

    @Test
    void commandsCommand_hasFilterOption() {
        var commandsCmd = cmd.getSubcommands().get("commands");
        assertNotNull(commandsCmd);
        var filterOption = commandsCmd.getCommandSpec().optionsMap().get("--filter");
        assertNotNull(filterOption, "--filter option should exist");
    }

    @Test
    void commandsCommand_hasTreeOption() {
        var commandsCmd = cmd.getSubcommands().get("commands");
        assertNotNull(commandsCmd);
        var treeOption = commandsCmd.getCommandSpec().optionsMap().get("--tree");
        assertNotNull(treeOption, "--tree option should exist");
    }

    @Test
    void commandsCommand_helpOutput() {
        var commandsCmd = cmd.getSubcommands().get("commands");
        assertNotNull(commandsCmd);
        StringWriter sw = new StringWriter();
        commandsCmd.usage(new PrintWriter(sw));
        String help = sw.toString();
        assertTrue(help.contains("commands"), "Help should mention commands");
    }
}
