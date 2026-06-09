package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the dashboard, monitor, compute, and replay-console CLI commands.
 * Tests picocli registration, option parsing, and help output without requiring a live server.
 */
@Tag("unit")
class CliNewCommandsE2ETest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void dashboardCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("dashboard"),
                "dashboard command should be registered");
    }

    @Test
    void dashboardCommand_hasRefreshOption() {
        var dashCmd = cmd.getSubcommands().get("dashboard");
        assertNotNull(dashCmd);
        var refreshOption = dashCmd.getCommandSpec().optionsMap().get("--refresh");
        assertNotNull(refreshOption, "--refresh option should exist");
        assertEquals("5", refreshOption.defaultValue(),
                "Default refresh interval should be 5 seconds");
    }

    @Test
    void dashboardCommand_helpOutput() {
        var dashCmd = cmd.getSubcommands().get("dashboard");
        assertNotNull(dashCmd);
        StringWriter sw = new StringWriter();
        dashCmd.usage(new PrintWriter(sw));
        String help = sw.toString();
        assertTrue(help.contains("dashboard"), "Help should mention dashboard");
        assertTrue(help.contains("--refresh"), "Help should show --refresh option");
        assertTrue(help.contains("auto-refresh"), "Help should mention auto-refresh");
    }

    @Test
    void monitorCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("monitor"),
                "monitor command should be registered");
    }

    @Test
    void monitorCommand_hasAllSubcommands() {
        var monitorCmd = cmd.getSubcommands().get("monitor");
        assertNotNull(monitorCmd);
        var subs = monitorCmd.getSubcommands();
        assertTrue(subs.containsKey("portfolio"), "Should have portfolio subcommand");
        assertTrue(subs.containsKey("broker"), "Should have broker subcommand");
        assertTrue(subs.containsKey("positions"), "Should have positions subcommand");
        assertTrue(subs.containsKey("orders"), "Should have orders subcommand");
    }

    @Test
    void monitorSubcommands_allHaveRefreshOptionWithDefault3() {
        var monitorCmd = cmd.getSubcommands().get("monitor");
        assertNotNull(monitorCmd);
        for (var sub : monitorCmd.getSubcommands().values()) {
            var refreshOption = sub.getCommandSpec().optionsMap().get("--refresh");
            assertNotNull(refreshOption, sub.getCommandName() + " should have --refresh");
            assertEquals("3", refreshOption.defaultValue(),
                    sub.getCommandName() + " default refresh should be 3 seconds");
        }
    }

    @Test
    void monitorPortfolio_helpOutput() {
        var monitorCmd = cmd.getSubcommands().get("monitor");
        var portfolioCmd = monitorCmd.getSubcommands().get("portfolio");
        StringWriter sw = new StringWriter();
        portfolioCmd.usage(new PrintWriter(sw));
        assertTrue(sw.toString().contains("portfolio"),
                "Portfolio help should mention portfolio");
    }

    @Test
    void computeCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("compute"),
                "compute command should be registered");
    }

    @Test
    void computeCommand_hasAllSubcommands() {
        var computeCmd = cmd.getSubcommands().get("compute");
        assertNotNull(computeCmd);
        var subs = computeCmd.getSubcommands();
        assertTrue(subs.containsKey("rsi"), "Should have rsi subcommand");
        assertTrue(subs.containsKey("ema"), "Should have ema subcommand");
        assertTrue(subs.containsKey("sma"), "Should have sma subcommand");
        assertTrue(subs.containsKey("vwap"), "Should have vwap subcommand");
        assertTrue(subs.containsKey("atr"), "Should have atr subcommand");
    }

    @Test
    void computeSubcommands_allHaveIntervalOption() {
        var computeCmd = cmd.getSubcommands().get("compute");
        assertNotNull(computeCmd);
        for (var sub : computeCmd.getSubcommands().values()) {
            var intervalOption = sub.getCommandSpec().optionsMap().get("--interval");
            assertNotNull(intervalOption, sub.getCommandName() + " should have --interval");
            assertEquals("5m", intervalOption.defaultValue(),
                    sub.getCommandName() + " default interval should be 5m");
        }
    }

    @Test
    void computeRsi_hasPeriodOption() {
        var computeCmd = cmd.getSubcommands().get("compute");
        var rsiCmd = computeCmd.getSubcommands().get("rsi");
        assertNotNull(rsiCmd);
        var periodOption = rsiCmd.getCommandSpec().optionsMap().get("--period");
        assertNotNull(periodOption, "RSI should have --period option");
        assertEquals("14", periodOption.defaultValue(), "RSI default period should be 14");
    }

    @Test
    void computeEma_hasPeriodOption() {
        var computeCmd = cmd.getSubcommands().get("compute");
        var emaCmd = computeCmd.getSubcommands().get("ema");
        assertNotNull(emaCmd);
        var periodOption = emaCmd.getCommandSpec().optionsMap().get("--period");
        assertNotNull(periodOption, "EMA should have --period option");
        assertEquals("20", periodOption.defaultValue(), "EMA default period should be 20");
    }

    @Test
    void computeAtr_hasPeriodOption() {
        var computeCmd = cmd.getSubcommands().get("compute");
        var atrCmd = computeCmd.getSubcommands().get("atr");
        assertNotNull(atrCmd);
        var periodOption = atrCmd.getCommandSpec().optionsMap().get("--period");
        assertNotNull(periodOption, "ATR should have --period option");
        assertEquals("14", periodOption.defaultValue(), "ATR default period should be 14");
    }

    @Test
    void replayConsoleCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("replay-console"),
                "replay-console command should be registered");
    }

    @Test
    void replayConsoleCommand_hasRequiredOptions() {
        var replayCmd = cmd.getSubcommands().get("replay-console");
        assertNotNull(replayCmd);
        var spec = replayCmd.getCommandSpec();
        assertNotNull(spec.optionsMap().get("--symbol"), "Should have --symbol option");
        assertNotNull(spec.optionsMap().get("--from"), "Should have --from option");
        assertNotNull(spec.optionsMap().get("--to"), "Should have --to option");
        assertNotNull(spec.optionsMap().get("--speed"), "Should have --speed option");
    }

    @Test
    void replayConsoleCommand_speedDefaultIs1() {
        var replayCmd = cmd.getSubcommands().get("replay-console");
        assertNotNull(replayCmd);
        var speedOption = replayCmd.getCommandSpec().optionsMap().get("--speed");
        assertNotNull(speedOption);
        assertEquals("1", speedOption.defaultValue(),
                "Default playback speed should be 1x");
    }

    @Test
    void replayConsoleCommand_helpOutput() {
        var replayCmd = cmd.getSubcommands().get("replay-console");
        assertNotNull(replayCmd);
        StringWriter sw = new StringWriter();
        replayCmd.usage(new PrintWriter(sw));
        String help = sw.toString();
        assertTrue(help.contains("replay-console"), "Help should mention replay-console");
        assertTrue(help.contains("--symbol"), "Help should show --symbol option");
        assertTrue(help.contains("--from"), "Help should show --from option");
        assertTrue(help.contains("--to"), "Help should show --to option");
    }

    @Test
    void topLevelHelp_listsAllNewCommands() {
        StringWriter sw = new StringWriter();
        cmd.usage(new PrintWriter(sw));
        String help = sw.toString();
        assertTrue(help.contains("dashboard"), "Top-level help should list dashboard");
        assertTrue(help.contains("monitor"), "Top-level help should list monitor");
        assertTrue(help.contains("compute"), "Top-level help should list compute");
        assertTrue(help.contains("replay-console"), "Top-level help should list replay-console");
    }

    @Test
    void computeSubcommandCount_isExactly5() {
        var computeCmd = cmd.getSubcommands().get("compute");
        assertNotNull(computeCmd);
        assertEquals(5, computeCmd.getSubcommands().size(),
                "compute should have exactly 5 subcommands (rsi, ema, sma, vwap, atr)");
    }

    @Test
    void monitorSubcommandCount_isExactly4() {
        var monitorCmd = cmd.getSubcommands().get("monitor");
        assertNotNull(monitorCmd);
        assertEquals(4, monitorCmd.getSubcommands().size(),
                "monitor should have exactly 4 subcommands (portfolio, broker, positions, orders)");
    }
}
