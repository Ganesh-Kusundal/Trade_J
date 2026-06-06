package com.tradej.cli.command;

import com.tradej.cli.CliOperations;
import com.tradej.cli.TradeCli;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.time.LocalDate;
import java.util.concurrent.Callable;

@Command(name = "broker", subcommands = {
        CliBrokerGatewayCommands.DhanGroup.class,
        CliBrokerGatewayCommands.UpstoxGroup.class,
        CliBrokerGatewayCommands.ValidateCmd.class,
        CliBrokerGatewayCommands.InspectCmd.class,
        CliBrokerGatewayCommands.CapabilitiesCmd.class
}, description = "Broker gateway commands (dhan, upstox, validate, inspect, capabilities)")
public final class CliBrokerGatewayCommands {

    @ParentCommand TradeCli root;

    abstract static class NamedBrokerCmd implements Callable<Integer> {
        @ParentCommand CliBrokerGatewayCommands gateway;
        @Override public Integer call() throws Exception { run(gateway.root.ops()); return 0; }
        abstract void run(CliOperations ops) throws Exception;
    }

    // ── Dhan group ──────────────────────────────────────────────────

    @Command(name = "dhan", subcommands = {
            DhanQuoteCmd.class, DhanDepthCmd.class, DhanLtpCmd.class,
            DhanChainCmd.class, DhanHistoricalCmd.class,
            DhanBalanceCmd.class, DhanPositionsCmd.class, DhanOrdersCmd.class
    }, description = "Dhan broker commands")
    static final class DhanGroup {
        @ParentCommand CliBrokerGatewayCommands gateway;
    }

    abstract static class DhanAction implements Callable<Integer> {
        @ParentCommand DhanGroup dhan;
        @Override public Integer call() throws Exception { run(dhan.gateway.root.ops()); return 0; }
        abstract void run(CliOperations ops) throws Exception;
    }

    @Command(name = "quote", description = "Get quote from Dhan")
    static final class DhanQuoteCmd extends DhanAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override void run(CliOperations ops) { ops.gatewayQuote("dhan", symbol, segment); }
    }

    @Command(name = "depth", description = "Get market depth from Dhan")
    static final class DhanDepthCmd extends DhanAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override void run(CliOperations ops) { ops.gatewayDepth("dhan", symbol, segment); }
    }

    @Command(name = "ltp", description = "Get LTP from Dhan")
    static final class DhanLtpCmd extends DhanAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override void run(CliOperations ops) { ops.gatewayLtp("dhan", symbol, segment); }
    }

    @Command(name = "chain", description = "Get option chain from Dhan")
    static final class DhanChainCmd extends DhanAction {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--expiry") String expiry;
        @Override void run(CliOperations ops) { ops.gatewayOptionChain("dhan", underlying, segment, expiry); }
    }

    @Command(name = "historical", description = "Get historical candles from Dhan")
    static final class DhanHistoricalCmd extends DhanAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--interval", defaultValue = "5m") String interval;
        @Option(names = "--from") LocalDate from;
        @Option(names = "--to") LocalDate to;
        @Override void run(CliOperations ops) {
            LocalDate end = to == null ? LocalDate.now() : to;
            LocalDate start = from == null ? end.minusDays(89) : from;
            ops.gatewayHistorical("dhan", symbol, segment, interval, start, end);
        }
    }

    @Command(name = "balance", description = "Get balance from Dhan")
    static final class DhanBalanceCmd extends DhanAction {
        @Override void run(CliOperations ops) { ops.gatewayBalance("dhan"); }
    }

    @Command(name = "positions", description = "Get positions from Dhan")
    static final class DhanPositionsCmd extends DhanAction {
        @Override void run(CliOperations ops) { ops.gatewayPositions("dhan"); }
    }

    @Command(name = "orders", description = "Get orders from Dhan")
    static final class DhanOrdersCmd extends DhanAction {
        @Override void run(CliOperations ops) { ops.gatewayOrders("dhan"); }
    }

    // ── Upstox group ────────────────────────────────────────────────

    @Command(name = "upstox", subcommands = {
            UpstoxQuoteCmd.class, UpstoxChainCmd.class
    }, description = "Upstox broker commands")
    static final class UpstoxGroup {
        @ParentCommand CliBrokerGatewayCommands gateway;
    }

    abstract static class UpstoxAction implements Callable<Integer> {
        @ParentCommand UpstoxGroup upstox;
        @Override public Integer call() throws Exception { run(upstox.gateway.root.ops()); return 0; }
        abstract void run(CliOperations ops) throws Exception;
    }

    @Command(name = "quote", description = "Get quote from Upstox")
    static final class UpstoxQuoteCmd extends UpstoxAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override void run(CliOperations ops) { ops.gatewayQuote("upstox", symbol, segment); }
    }

    @Command(name = "chain", description = "Get option chain from Upstox")
    static final class UpstoxChainCmd extends UpstoxAction {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--expiry") String expiry;
        @Override void run(CliOperations ops) { ops.gatewayOptionChain("upstox", underlying, segment, expiry); }
    }

    // ── Validate & Inspect ──────────────────────────────────────────

    @Command(name = "validate", description = "Run broker certification suite")
    static final class ValidateCmd extends NamedBrokerCmd {
        @Parameters(index = "0", defaultValue = "dhan") String brokerName;
        @Parameters(index = "1", defaultValue = "RELIANCE") String symbol;
        @Parameters(index = "2", defaultValue = "NSE_EQ") String segment;
        @Override void run(CliOperations ops) { ops.gatewayValidate(brokerName, symbol, segment); }
    }

    @Command(name = "inspect", description = "Inspect broker capabilities")
    static final class InspectCmd extends NamedBrokerCmd {
        @Parameters(index = "0", defaultValue = "dhan") String brokerName;
        @Override void run(CliOperations ops) { ops.gatewayInspect(brokerName); }
    }

    @Command(name = "capabilities", description = "List broker capabilities with metadata")
    static final class CapabilitiesCmd extends NamedBrokerCmd {
        @Parameters(index = "0", defaultValue = "dhan") String brokerName;
        @Option(names = "--json", description = "Output as JSON") boolean jsonOutput;
        @Override void run(CliOperations ops) { ops.gatewayCapabilities(brokerName, jsonOutput); }
    }
}
