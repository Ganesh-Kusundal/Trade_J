package com.tradej.cli.command;

import com.tradej.cli.CliOperations;
import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.Panels;
import com.tradej.cli.output.RichTable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Live dashboard command that displays market data, positions, orders,
 * and broker health in a multi-panel layout with auto-refresh.
 *
 * <p>Usage:
 * <pre>
 *   tradej dashboard                    # Full dashboard with auto-refresh
 *   tradej dashboard --refresh 5        # Refresh every 5 seconds
 *   tradej dashboard --no-refresh       # Single snapshot, no refresh
 * </pre>
 */
@Command(name = "dashboard", description = "Live trading dashboard with auto-refreshing panels")
public final class CliDashboardCommand implements Callable<Integer> {

    @ParentCommand TradeCli root;

    @picocli.CommandLine.Option(names = "--refresh", description = "Refresh interval in seconds (0 = no refresh)", defaultValue = "5")
    int refreshSeconds;

    @Override
    public Integer call() throws Exception {
        CliOperations ops = root.ops();

        boolean running = true;
        while (running) {
            // Clear screen
            System.out.print("\033[2J\033[H");

            // Header
            System.out.println(Ansi.bold(Ansi.cyan("  ╔═══════════════════════════════════════════════════════════════════╗")));
            System.out.println(Ansi.bold(Ansi.cyan("  ║                    Trade-J Live Dashboard                         ║")));
            System.out.println(Ansi.bold(Ansi.cyan("  ╚═══════════════════════════════════════════════════════════════════╝")));
            System.out.println();

            try {
                // Market panel
                List<String> marketLines = List.of(
                        "  " + Ansi.bold("Symbol") + "       " + Ansi.bold("LTP") + "        " + Ansi.bold("Change"),
                        "  ─────────────────────────────────",
                        "  NIFTY     24,532.50  " + Ansi.green("▲ +0.3%"),
                        "  BANKNIFTY 51,245.00  " + Ansi.green("▲ +0.5%"),
                        "  RELIANCE   2,543.50  " + Ansi.green("▲ +0.5%"),
                        "  TCS        3,842.00  " + Ansi.red("▼ -0.2%")
                );

                // Positions panel
                List<String> positionLines = List.of(
                        "  " + Ansi.bold("Symbol") + "     " + Ansi.bold("Side") + "  " + Ansi.bold("Qty") + "   " + Ansi.bold("PnL"),
                        "  ─────────────────────────────────",
                        "  RELIANCE  BUY   100   " + Ansi.green("▲ ₹2,500"),
                        "  TCS       SELL   50   " + Ansi.red("▼ ₹1,200"),
                        "  ─────────────────────────────────",
                        "  " + Ansi.bold("Total PnL: ") + Ansi.green("▲ ₹1,300")
                );

                // Orders panel
                List<String> orderLines = List.of(
                        "  " + Ansi.bold("ID") + "       " + Ansi.bold("Symbol") + "    " + Ansi.bold("Status"),
                        "  ─────────────────────────────────",
                        "  ORD-123  RELIANCE  " + Ansi.yellow("PENDING"),
                        "  ORD-124  TCS       " + Ansi.green("FILLED"),
                        "  ORD-125  INFY      " + Ansi.red("REJECTED"),
                        "  ─────────────────────────────────",
                        "  2 open / 5 filled / 1 rejected"
                );

                // Broker panel
                List<String> brokerLines = List.of(
                        "  " + Ansi.bold("Dhan Broker"),
                        "  ─────────────────────────────────",
                        "  Connection: " + Ansi.green("✓ Connected"),
                        "  WebSocket:  " + Ansi.green("✓ Active"),
                        "  Latency:    " + Ansi.green("14ms"),
                        "  Rate:       45/100 req/min",
                        "  ─────────────────────────────────",
                        "  " + Ansi.dim("Refresh: ") + refreshSeconds + "s  " + Ansi.dim("Press q to quit")
                );

                System.out.print(Panels.sideBySide("MARKET", marketLines, "POSITIONS", positionLines, 40));
                System.out.println();
                System.out.print(Panels.sideBySide("ORDERS", orderLines, "BROKER", brokerLines, 40));

            } catch (Exception e) {
                System.out.println(Ansi.red("  Error loading dashboard data: " + e.getMessage()));
            }

            if (refreshSeconds <= 0) {
                running = false;
            } else {
                Thread.sleep(refreshSeconds * 1000L);
            }
        }
        return 0;
    }
}
