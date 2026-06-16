package com.tradej.cli.command;

import com.tradej.cli.CliOperations;
import com.tradej.cli.TradeCli;
import com.tradej.core.domain.config.DefaultSegments;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.time.LocalDate;
import java.util.concurrent.Callable;

@Command(name = "broker", subcommands = {
        CliBrokerGatewayCommands.DhanGroup.class,
        CliBrokerGatewayCommands.UpstoxGroup.class,
        CliBrokerGatewayCommands.IciciGroup.class,
        CliBrokerGatewayCommands.ValidateCmd.class,
        CliBrokerGatewayCommands.InspectCmd.class,
        CliBrokerGatewayCommands.CapabilitiesCmd.class
}, description = "Broker gateway commands (dhan, upstox, icici, validate, inspect, capabilities)")
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
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_INDEX_SEGMENT) String segment;
        @Override void run(CliOperations ops) { ops.gatewayQuote("dhan", symbol, segment); }
    }

    @Command(name = "depth", description = "Get market depth from Dhan")
    static final class DhanDepthCmd extends DhanAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_INDEX_SEGMENT) String segment;
        @Override void run(CliOperations ops) { ops.gatewayDepth("dhan", symbol, segment); }
    }

    @Command(name = "ltp", description = "Get LTP from Dhan")
    static final class DhanLtpCmd extends DhanAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_INDEX_SEGMENT) String segment;
        @Override void run(CliOperations ops) { ops.gatewayLtp("dhan", symbol, segment); }
    }

    @Command(name = "chain", description = "Get option chain from Dhan")
    static final class DhanChainCmd extends DhanAction {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_INDEX_SEGMENT) String segment;
        @Option(names = "--expiry") String expiry;
        @Override void run(CliOperations ops) { ops.gatewayOptionChain("dhan", underlying, segment, expiry); }
    }

    @Command(name = "historical", description = "Get historical candles from Dhan")
    static final class DhanHistoricalCmd extends DhanAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_INDEX_SEGMENT) String segment;
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
            UpstoxQuoteCmd.class, UpstoxDepthCmd.class, UpstoxLtpCmd.class,
            UpstoxChainCmd.class, UpstoxHistoricalCmd.class,
            UpstoxBalanceCmd.class, UpstoxPositionsCmd.class, UpstoxOrdersCmd.class
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
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment;
        @Override void run(CliOperations ops) { ops.gatewayQuote("upstox", symbol, segment); }
    }

    @Command(name = "depth", description = "Get market depth from Upstox")
    static final class UpstoxDepthCmd extends UpstoxAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment;
        @Override void run(CliOperations ops) { ops.gatewayDepth("upstox", symbol, segment); }
    }

    @Command(name = "ltp", description = "Get LTP from Upstox")
    static final class UpstoxLtpCmd extends UpstoxAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment;
        @Override void run(CliOperations ops) { ops.gatewayLtp("upstox", symbol, segment); }
    }

    @Command(name = "chain", description = "Get option chain from Upstox")
    static final class UpstoxChainCmd extends UpstoxAction {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_INDEX_SEGMENT) String segment;
        @Option(names = "--expiry") String expiry;
        @Override void run(CliOperations ops) { ops.gatewayOptionChain("upstox", underlying, segment, expiry); }
    }

    @Command(name = "historical", description = "Get historical candles from Upstox")
    static final class UpstoxHistoricalCmd extends UpstoxAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment;
        @Option(names = "--interval", defaultValue = "1m") String interval;
        @Option(names = "--from") LocalDate from;
        @Option(names = "--to") LocalDate to;
        @Override void run(CliOperations ops) {
            LocalDate end = to == null ? LocalDate.now() : to;
            LocalDate start = from == null ? end.minusDays(89) : from;
            ops.gatewayHistorical("upstox", symbol, segment, interval, start, end);
        }
    }

    @Command(name = "balance", description = "Get balance from Upstox")
    static final class UpstoxBalanceCmd extends UpstoxAction {
        @Override void run(CliOperations ops) { ops.gatewayBalance("upstox"); }
    }

    @Command(name = "positions", description = "Get positions from Upstox")
    static final class UpstoxPositionsCmd extends UpstoxAction {
        @Override void run(CliOperations ops) { ops.gatewayPositions("upstox"); }
    }

    @Command(name = "orders", description = "Get orders from Upstox")
    static final class UpstoxOrdersCmd extends UpstoxAction {
        @Override void run(CliOperations ops) { ops.gatewayOrders("upstox"); }
    }

    // ── ICICI group ─────────────────────────────────────────────────

    @Command(name = "icici", subcommands = {
            IciciQuoteCmd.class, IciciDepthCmd.class, IciciLtpCmd.class,
            IciciChainCmd.class, IciciHistoricalCmd.class,
            IciciBalanceCmd.class, IciciPositionsCmd.class, IciciOrdersCmd.class
    }, description = "ICICI broker commands")
    static final class IciciGroup {
        @ParentCommand CliBrokerGatewayCommands gateway;
    }

    abstract static class IciciAction implements Callable<Integer> {
        @ParentCommand IciciGroup icici;
        @Override public Integer call() throws Exception { run(icici.gateway.root.ops()); return 0; }
        abstract void run(CliOperations ops) throws Exception;
    }

    @Command(name = "quote", description = "Get quote from ICICI")
    static final class IciciQuoteCmd extends IciciAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment;
        @Override void run(CliOperations ops) { ops.gatewayQuote("icici", symbol, segment); }
    }

    @Command(name = "depth", description = "Get market depth from ICICI")
    static final class IciciDepthCmd extends IciciAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment;
        @Override void run(CliOperations ops) { ops.gatewayDepth("icici", symbol, segment); }
    }

    @Command(name = "ltp", description = "Get LTP from ICICI")
    static final class IciciLtpCmd extends IciciAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment;
        @Override void run(CliOperations ops) { ops.gatewayLtp("icici", symbol, segment); }
    }

    @Command(name = "chain", description = "Get option chain from ICICI")
    static final class IciciChainCmd extends IciciAction {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_INDEX_SEGMENT) String segment;
        @Option(names = "--expiry") String expiry;
        @Override void run(CliOperations ops) { ops.gatewayOptionChain("icici", underlying, segment, expiry); }
    }

    @Command(name = "historical", description = "Get historical candles from ICICI")
    static final class IciciHistoricalCmd extends IciciAction {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment;
        @Option(names = "--interval", defaultValue = "5m") String interval;
        @Option(names = "--from") LocalDate from;
        @Option(names = "--to") LocalDate to;
        @Override void run(CliOperations ops) {
            LocalDate end = to == null ? LocalDate.now() : to;
            LocalDate start = from == null ? end.minusDays(89) : from;
            ops.gatewayHistorical("icici", symbol, segment, interval, start, end);
        }
    }

    @Command(name = "balance", description = "Get balance from ICICI")
    static final class IciciBalanceCmd extends IciciAction {
        @Override void run(CliOperations ops) { ops.gatewayBalance("icici"); }
    }

    @Command(name = "positions", description = "Get positions from ICICI")
    static final class IciciPositionsCmd extends IciciAction {
        @Override void run(CliOperations ops) { ops.gatewayPositions("icici"); }
    }

    @Command(name = "orders", description = "Get orders from ICICI")
    static final class IciciOrdersCmd extends IciciAction {
        @Override void run(CliOperations ops) { ops.gatewayOrders("icici"); }
    }

    // ── Validate & Inspect ──────────────────────────────────────────

    @Command(name = "validate", description = "Run broker certification suite")
    static final class ValidateCmd extends NamedBrokerCmd {
        @Parameters(index = "0", defaultValue = "dhan") String brokerName;
        @Parameters(index = "1", defaultValue = "RELIANCE") String symbol;
        @Parameters(index = "2", defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment;
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
