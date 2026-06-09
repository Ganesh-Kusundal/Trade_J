package com.tradej.cli.command;

import com.tradej.cli.CliOperations;
import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Monitor command for live-updating single-panel views.
 *
 * <p>Usage:
 * <pre>
 *   tradej monitor portfolio            # Live portfolio with auto-refresh
 *   tradej monitor broker dhan          # Live broker health
 *   tradej monitor positions            # Live positions
 *   tradej monitor orders               # Live orders
 * </pre>
 */
@Command(name = "monitor", subcommands = {
        CliMonitorCommand.PortfolioMonitor.class,
        CliMonitorCommand.BrokerMonitor.class,
        CliMonitorCommand.PositionsMonitor.class,
        CliMonitorCommand.OrdersMonitor.class
}, description = "Live-updating single-panel monitors")
public final class CliMonitorCommand {
    @ParentCommand TradeCli root;

    abstract static class MonitorBase implements Callable<Integer> {
        @ParentCommand CliMonitorCommand monitor;
        @Option(names = "--refresh", description = "Refresh interval in seconds", defaultValue = "3")
        int refreshSeconds;

        @Override
        public Integer call() throws Exception {
            run(monitor.root.ops());
            return 0;
        }

        abstract void run(CliOperations ops) throws Exception;

        protected void clearScreen() {
            System.out.print("\033[2J\033[H");
        }

        protected String header(String title) {
            return Ansi.bold(Ansi.cyan("  ── " + title + " ──")) + "  " + Ansi.dim("refresh: " + refreshSeconds + "s  Ctrl+C to quit");
        }
    }

    @Command(name = "portfolio", description = "Live portfolio monitor")
    static final class PortfolioMonitor extends MonitorBase {
        @Override
        void run(CliOperations ops) throws Exception {
            while (true) {
                clearScreen();
                System.out.println(header("PORTFOLIO"));
                System.out.println();
                ops.balance();
                System.out.println();
                ops.brokerPositions();
                Thread.sleep(refreshSeconds * 1000L);
            }
        }
    }

    @Command(name = "broker", description = "Live broker health monitor")
    static final class BrokerMonitor extends MonitorBase {
        @Parameters(index = "0", defaultValue = "dhan") String broker;

        @Override
        void run(CliOperations ops) throws Exception {
            while (true) {
                clearScreen();
                System.out.println(header("BROKER: " + broker.toUpperCase()));
                System.out.println();
                ops.brokerHealthCheck(broker);
                Thread.sleep(refreshSeconds * 1000L);
            }
        }
    }

    @Command(name = "positions", description = "Live positions monitor")
    static final class PositionsMonitor extends MonitorBase {
        @Override
        void run(CliOperations ops) throws Exception {
            while (true) {
                clearScreen();
                System.out.println(header("POSITIONS"));
                System.out.println();
                ops.brokerPositions();
                Thread.sleep(refreshSeconds * 1000L);
            }
        }
    }

    @Command(name = "orders", description = "Live orders monitor")
    static final class OrdersMonitor extends MonitorBase {
        @Override
        void run(CliOperations ops) throws Exception {
            while (true) {
                clearScreen();
                System.out.println(header("ORDERS"));
                System.out.println();
                ops.orders();
                Thread.sleep(refreshSeconds * 1000L);
            }
        }
    }
}
