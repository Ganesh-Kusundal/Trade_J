package com.tradej.cli;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Top-level smoke test for {@link TradeCli} — verifies the command
 * parser accepts the most-used sub-commands and the new {@code dev}
 * and {@code parity} sub-commands.
 */
@Tag("unit")
class TradeCliSmokeTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void coreSubcommandsAreRegistered() {
        assertNotNull(cmd.getSubcommands().get("status"));
        assertNotNull(cmd.getSubcommands().get("runtime"));
        assertNotNull(cmd.getSubcommands().get("strategies"));
        assertNotNull(cmd.getSubcommands().get("parity"));
        assertNotNull(cmd.getSubcommands().get("dev"));
        assertNotNull(cmd.getSubcommands().get("replay"));
    }

    @Test
    void paritySubcommandAcceptsRequiredArgs() {
        // picocli 4.x: missing required options throw a ParameterException
        // when the command is executed; the parser should still find the
        // sub-command and report the missing --from / --to.
        int exit = cmd.execute("parity", "MyPlugin");
        assertNotNull(cmd.getSubcommands().get("parity"));
        // Non-zero exit expected because required options are missing.
        assertTrue(exit != 0, "parity without --from/--to should fail");
    }

    @Test
    void devSubcommandRunsWithoutBroker() {
        // The `tradej dev` banner is printed and the process exits 0 even
        // without a real broker session — it just announces the workflow.
        int exit = cmd.execute("dev");
        assertEquals(0, exit, "dev command should succeed with default args");
    }
}
