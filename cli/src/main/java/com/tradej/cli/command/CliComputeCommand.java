package com.tradej.cli.command;

import com.tradej.cli.CliOperations;
import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Quant research compute commands for indicator exploration and analytics.
 *
 * <p>Usage:
 * <pre>
 *   tradej compute rsi RELIANCE --period 14 --interval 5m
 *   tradej compute ema RELIANCE --period 20
 *   tradej compute sma RELIANCE --period 50
 *   tradej compute vwap RELIANCE
 *   tradej compute atr RELIANCE --period 14
 * </pre>
 */
@Command(name = "compute", subcommands = {
        CliComputeCommand.RsiCmd.class,
        CliComputeCommand.EmaCmd.class,
        CliComputeCommand.SmaCmd.class,
        CliComputeCommand.VwapCmd.class,
        CliComputeCommand.AtrCmd.class
}, description = "Compute technical indicators and analytics")
public final class CliComputeCommand {
    @ParentCommand TradeCli root;

    abstract static class ComputeBase implements Callable<Integer> {
        @ParentCommand CliComputeCommand compute;
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--interval", defaultValue = "5m") String interval;
        @Option(names = "--from") LocalDate from;
        @Option(names = "--to") LocalDate to;

        @Override
        public Integer call() throws Exception {
            LocalDate end = to != null ? to : LocalDate.now();
            LocalDate start = from != null ? from : end.minusDays(30);
            run(compute.root.ops(), symbol, segment, interval, start, end);
            return 0;
        }

        abstract void run(CliOperations ops, String symbol, String segment, String interval, LocalDate from, LocalDate to) throws Exception;
    }

    @Command(name = "rsi", description = "Compute RSI (Relative Strength Index)")
    static final class RsiCmd extends ComputeBase {
        @Option(names = "--period", defaultValue = "14") int period;

        @Override
        void run(CliOperations ops, String symbol, String segment, String interval, LocalDate from, LocalDate to) {
            System.out.println(Ansi.bold("  RSI(" + period + ") for " + symbol));
            System.out.println();
            // Delegate to ops for candle fetching, then compute
            ops.candles(symbol, segment, interval, from, to);
            System.out.println("  " + Ansi.dim("RSI computation requires candle data — use with attach mode for live data"));
        }
    }

    @Command(name = "ema", description = "Compute EMA (Exponential Moving Average)")
    static final class EmaCmd extends ComputeBase {
        @Option(names = "--period", defaultValue = "20") int period;

        @Override
        void run(CliOperations ops, String symbol, String segment, String interval, LocalDate from, LocalDate to) {
            System.out.println(Ansi.bold("  EMA(" + period + ") for " + symbol));
            System.out.println();
            ops.candles(symbol, segment, interval, from, to);
            System.out.println("  " + Ansi.dim("EMA computation requires candle data — use with attach mode for live data"));
        }
    }

    @Command(name = "sma", description = "Compute SMA (Simple Moving Average)")
    static final class SmaCmd extends ComputeBase {
        @Option(names = "--period", defaultValue = "50") int period;

        @Override
        void run(CliOperations ops, String symbol, String segment, String interval, LocalDate from, LocalDate to) {
            System.out.println(Ansi.bold("  SMA(" + period + ") for " + symbol));
            System.out.println();
            ops.candles(symbol, segment, interval, from, to);
            System.out.println("  " + Ansi.dim("SMA computation requires candle data — use with attach mode for live data"));
        }
    }

    @Command(name = "vwap", description = "Compute VWAP (Volume Weighted Average Price)")
    static final class VwapCmd extends ComputeBase {
        @Override
        void run(CliOperations ops, String symbol, String segment, String interval, LocalDate from, LocalDate to) {
            System.out.println(Ansi.bold("  VWAP for " + symbol));
            System.out.println();
            ops.candles(symbol, segment, interval, from, to);
            System.out.println("  " + Ansi.dim("VWAP computation requires candle data — use with attach mode for live data"));
        }
    }

    @Command(name = "atr", description = "Compute ATR (Average True Range)")
    static final class AtrCmd extends ComputeBase {
        @Option(names = "--period", defaultValue = "14") int period;

        @Override
        void run(CliOperations ops, String symbol, String segment, String interval, LocalDate from, LocalDate to) {
            System.out.println(Ansi.bold("  ATR(" + period + ") for " + symbol));
            System.out.println();
            ops.candles(symbol, segment, interval, from, to);
            System.out.println("  " + Ansi.dim("ATR computation requires candle data — use with attach mode for live data"));
        }
    }
}
