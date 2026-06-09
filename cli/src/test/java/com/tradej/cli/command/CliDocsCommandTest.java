package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliDocsCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void docsCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("docs"),
                "docs command should be registered");
    }

    @Test
    void docsCommand_hasGenerateSubcommand() {
        var docsCmd = cmd.getSubcommands().get("docs");
        assertNotNull(docsCmd);
        assertTrue(docsCmd.getSubcommands().containsKey("generate"),
                "docs should have generate subcommand");
    }

    @Test
    void generateSubcommand_hasFormatOption() {
        var docsCmd = cmd.getSubcommands().get("docs");
        assertNotNull(docsCmd);
        var genCmd = docsCmd.getSubcommands().get("generate");
        assertNotNull(genCmd);
        var formatOption = genCmd.getCommandSpec().optionsMap().get("--format");
        assertNotNull(formatOption, "--format option should exist");
        assertEquals("markdown", formatOption.defaultValue());
    }
}
