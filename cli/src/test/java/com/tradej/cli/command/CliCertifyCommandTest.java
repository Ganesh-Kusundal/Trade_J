package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliCertifyCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void certifyCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("certify"),
                "certify command should be registered");
    }

    @Test
    void certifyCommand_hasSubcommands() {
        var certifyCmd = cmd.getSubcommands().get("certify");
        assertNotNull(certifyCmd);
        var subs = certifyCmd.getSubcommands();
        assertTrue(subs.containsKey("replay"), "should have replay subcommand");
        assertTrue(subs.containsKey("simulation"), "should have simulation subcommand");
    }

    @Test
    void certifyCommand_hasStoreOption() {
        var certifyCmd = cmd.getSubcommands().get("certify");
        assertNotNull(certifyCmd);
        var storeOption = certifyCmd.getCommandSpec().optionsMap().get("--store");
        assertNotNull(storeOption, "--store option should exist");
    }

    @Test
    void certifyCommand_hasArtifactsDirOption() {
        var certifyCmd = cmd.getSubcommands().get("certify");
        assertNotNull(certifyCmd);
        var dirOption = certifyCmd.getCommandSpec().optionsMap().get("--artifacts-dir");
        assertNotNull(dirOption, "--artifacts-dir option should exist");
        assertEquals("CertificationArtifacts", dirOption.defaultValue());
    }
}
