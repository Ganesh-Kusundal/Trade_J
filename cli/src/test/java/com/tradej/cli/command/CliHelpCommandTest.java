package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliHelpCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void helpCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("help"),
                "help command should be registered");
    }

    @Test
    void helpCommand_hasDescription() {
        var helpCmd = cmd.getSubcommands().get("help");
        assertNotNull(helpCmd);
        String[] desc = helpCmd.getCommandSpec().usageMessage().description();
        assertTrue(desc.length > 0);
        assertTrue(desc[0].contains("help") || desc[0].contains("Help"),
                "description should mention help");
    }

    @Test
    void helpCommand_acceptsTopicParameter() {
        var helpCmd = cmd.getSubcommands().get("help");
        assertNotNull(helpCmd);
        var params = helpCmd.getCommandSpec().positionalParameters();
        assertFalse(params.isEmpty(), "help should accept a topic parameter");
    }

    @Test
    void helpCommand_topicDescriptions_exist() {
        var topics = java.util.List.of("broker", "replay", "analytics",
                "flows", "architecture",
                "data", "download", "portfolio", "backtest", "scan", "doctor",
                "events", "modules", "brokers", "plugins", "datasources",
                "capabilities", "commands");
        for (String topic : topics) {
            CommandLine sub = cmd.getSubcommands().get(topic);
            assertNotNull(sub, "topic '" + topic + "' should be a registered command");
        }
    }
}
