package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliFlowsCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void flowsCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("flows"),
                "flows command should be registered");
    }

    @Test
    void flowsCommand_hasAllSubcommands() {
        var flowsCmd = cmd.getSubcommands().get("flows");
        assertNotNull(flowsCmd);
        var subs = flowsCmd.getSubcommands();
        assertTrue(subs.containsKey("quote"), "should have quote subcommand");
        assertTrue(subs.containsKey("order"), "should have order subcommand");
        assertTrue(subs.containsKey("replay"), "should have replay subcommand");
        assertTrue(subs.containsKey("simulation"), "should have simulation subcommand");
        assertTrue(subs.containsKey("risk"), "should have risk subcommand");
    }

    @Test
    void flowsMap_containsAllFlows() {
        assertTrue(CliFlowsCommand.FLOWS.containsKey("quote"));
        assertTrue(CliFlowsCommand.FLOWS.containsKey("order"));
        assertTrue(CliFlowsCommand.FLOWS.containsKey("replay"));
        assertTrue(CliFlowsCommand.FLOWS.containsKey("simulation"));
        assertTrue(CliFlowsCommand.FLOWS.containsKey("risk"));
        assertEquals(5, CliFlowsCommand.FLOWS.size());
    }

    @Test
    void flowDefinitions_haveSteps() {
        for (var entry : CliFlowsCommand.FLOWS.entrySet()) {
            var flow = entry.getValue();
            assertNotNull(flow.title(), entry.getKey() + " should have a title");
            assertNotNull(flow.entryPoint(), entry.getKey() + " should have an entry point");
            assertTrue(flow.steps().length >= 3, entry.getKey() + " should have at least 3 steps");
        }
    }

    @Test
    void flowsCommand_hasDescription() {
        var flowsCmd = cmd.getSubcommands().get("flows");
        assertNotNull(flowsCmd);
        String[] desc = flowsCmd.getCommandSpec().usageMessage().description();
        assertTrue(desc.length > 0);
    }
}
