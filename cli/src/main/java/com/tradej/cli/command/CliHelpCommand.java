package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "help", description = "Topic-based help — tradej help <topic>")
public final class CliHelpCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Parameters(index = "0", arity = "0..1", description = "Help topic: broker, replay, simulation, analytics, indicators, gateway, query, flows, architecture")
    String topic;

    private static final Map<String, String> TOPIC_DESCRIPTIONS = Map.ofEntries(
            Map.entry("broker", "Broker connectivity, market data, orders, portfolio"),
            Map.entry("replay", "Historical event replay from DuckDB and Chronicle Queue"),
            Map.entry("simulation", "Order matching engine with PnL tracking"),
            Map.entry("analytics", "Market analytics — PCR, OI, max pain, support/resistance"),
            Map.entry("indicators", "SPI-discovered technical indicators (RSI, EMA, SMA, ATR, VWAP, OBV)"),
            Map.entry("gateway", "Broker gateway — unified facade for all brokers"),
            Map.entry("query", "SQL queries against DuckDB analytics warehouse"),
            Map.entry("flows", "Runtime flow documentation — quote, order, replay, simulation, risk"),
            Map.entry("architecture", "Platform architecture — modules, patterns, events, constraints"),
            Map.entry("data", "Standalone market data commands (no trade-app required)"),
            Map.entry("download", "Historical data download jobs — rolling options, equity"),
            Map.entry("portfolio", "Standalone portfolio analysis — balance, positions, holdings, PnL"),
            Map.entry("backtest", "Standalone backtesting — SMA crossover, buy-hold strategies"),
            Map.entry("scan", "Intraday stock scanner with profile-based criteria"),
            Map.entry("risk", "Risk management — kill switch, daily loss, position limits"),
            Map.entry("doctor", "System health check — Java, drivers, SPI, runtime paths")
    );

    @Override
    public Integer call() {
        if (topic == null || topic.isBlank()) {
            printTopicIndex();
            return 0;
        }

        CommandLine cmd = new CommandLine(new TradeCli());
        CommandLine sub = cmd.getSubcommands().get(topic);

        if (sub != null) {
            StringWriter sw = new StringWriter();
            sub.usage(new PrintWriter(sw));
            System.out.println(sw);
            return 0;
        }

        String description = TOPIC_DESCRIPTIONS.get(topic);
        if (description != null) {
            System.out.println(Ansi.bold("\n  " + topic));
            System.out.println("  " + description);
            System.out.println(Ansi.dim("\n  Run 'tradej " + topic + " --help' for detailed usage."));
        } else {
            System.out.println(Ansi.red("  Unknown topic: " + topic));
            printTopicIndex();
        }
        return 0;
    }

    private void printTopicIndex() {
        System.out.println(Ansi.bold("\n  Trade-J Help Topics\n"));
        System.out.println("  Usage: tradej help <topic>\n");

        for (Map.Entry<String, String> entry : TOPIC_DESCRIPTIONS.entrySet()) {
            System.out.printf("  %-16s %s%n",
                    Ansi.cyan(entry.getKey()),
                    entry.getValue());
        }

        System.out.println(Ansi.dim("\n  Run 'tradej commands' for full command listing."));
        System.out.println(Ansi.dim("  Run 'tradej capabilities' for platform capability summary."));
    }
}
