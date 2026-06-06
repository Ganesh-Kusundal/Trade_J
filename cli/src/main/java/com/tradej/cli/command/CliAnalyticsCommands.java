package com.tradej.cli.command;

import com.tradej.cli.CliOperations;
import com.tradej.cli.TradeCli;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "analytics", subcommands = {
        CliAnalyticsCommands.PcrCmd.class,
        CliAnalyticsCommands.TopOiCmd.class,
        CliAnalyticsCommands.TopVolumeCmd.class,
        CliAnalyticsCommands.MaxPainCmd.class,
        CliAnalyticsCommands.SupportCmd.class
}, description = "Market analytics commands (PCR, OI, max pain, support/resistance)")
public final class CliAnalyticsCommands {

    @ParentCommand TradeCli root;

    abstract static class AnalyticsBase implements Callable<Integer> {
        @ParentCommand CliAnalyticsCommands parent;
        @Override public Integer call() throws Exception { run(parent.root.ops()); return 0; }
        abstract void run(CliOperations ops) throws Exception;
    }

    @Command(name = "pcr", description = "Put-Call Ratio for underlying")
    static final class PcrCmd extends AnalyticsBase {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override void run(CliOperations ops) { ops.marketPcr(underlying, segment); }
    }

    @Command(name = "top-oi", description = "Top open interest strikes")
    static final class TopOiCmd extends AnalyticsBase {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--top", defaultValue = "10") int top;
        @Override void run(CliOperations ops) { ops.marketTopOi(underlying, segment, top); }
    }

    @Command(name = "top-volume", description = "Top volume strikes")
    static final class TopVolumeCmd extends AnalyticsBase {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--top", defaultValue = "10") int top;
        @Override void run(CliOperations ops) { ops.marketTopVolume(underlying, segment, top); }
    }

    @Command(name = "max-pain", description = "Max pain strike for underlying")
    static final class MaxPainCmd extends AnalyticsBase {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override void run(CliOperations ops) { ops.marketMaxPain(underlying, segment); }
    }

    @Command(name = "support", description = "Support/resistance levels")
    static final class SupportCmd extends AnalyticsBase {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override void run(CliOperations ops) { ops.marketSupport(underlying, segment); }
    }
}
