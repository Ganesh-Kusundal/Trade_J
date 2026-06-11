package com.tradej.cli.command;

import com.tradej.historical.ingest.calendar.TradingCalendarStore;
import com.tradej.historical.ingest.canonical.CanonicalBarQuery;
import com.tradej.historical.ingest.canonical.CanonicalPaths;
import com.tradej.historical.ingest.canonical.MultiIntervalGenerator;
import com.tradej.historical.ingest.canonical.ParquetHistoricalDataStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import com.tradej.cli.TradeCli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "parquet", description = "Canonical parquet data platform — query, quality, resample",
        subcommands = {
                CliParquetCommands.SummaryCmd.class,
                CliParquetCommands.QualityCmd.class,
                CliParquetCommands.IntervalsCmd.class,
                CliParquetCommands.ResampleCmd.class,
                CliParquetCommands.QueryCmd.class,
                CliParquetCommands.GapsCmd.class,
                CliParquetCommands.SyncCmd.class
        })
public final class CliParquetCommands implements Callable<Integer> {

    @ParentCommand TradeCli root;

    private static final Path DEFAULT_DATA_ROOT = Path.of("data");

    @Override
    public Integer call() {
        System.out.println("Use: parquet summary|quality|intervals|resample|query");
        return 0;
    }

    @Command(name = "summary", description = "Show data platform summary (symbols, bars, intervals)")
    static final class SummaryCmd implements Callable<Integer> {
        @Option(names = "--segment", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--data-root", defaultValue = "data") Path dataRoot;

        @Override
        public Integer call() throws Exception {
            Path barsRoot = CanonicalPaths.barsRoot(dataRoot);
            if (!Files.isDirectory(barsRoot)) {
                System.out.println("No canonical parquet data found at " + barsRoot);
                System.out.println("Run migration first or download historical data.");
                return 1;
            }
            try (CanonicalBarQuery query = new CanonicalBarQuery(barsRoot)) {
                Map<String, Object> summary = query.summary(segment);
                System.out.println("=== Canonical Parquet Summary (" + segment + ") ===");
                System.out.println("  Data root:   " + barsRoot.toAbsolutePath());
                System.out.println("  Total bars:  " + summary.get("totalBars"));
                System.out.println("  Symbols:     " + summary.get("symbols"));
                System.out.println("  Intervals:   " + summary.get("intervals"));
                long earliestMs = ((Number) summary.getOrDefault("earliestMs", 0L)).longValue();
                long latestMs = ((Number) summary.getOrDefault("latestMs", 0L)).longValue();
                if (earliestMs > 0) {
                    System.out.println("  Date range:  " + java.time.Instant.ofEpochMilli(earliestMs)
                            .atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate()
                            + " → " + java.time.Instant.ofEpochMilli(latestMs)
                            .atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate());
                }

                System.out.println("\n  Interval breakdown:");
                for (String interval : List.of("1m", "5m", "15m", "1d")) {
                    List<String> symbols = query.availableSymbols(segment, interval);
                    if (!symbols.isEmpty()) {
                        long totalBars = 0;
                        for (String sym : symbols) {
                            totalBars += query.countBars(sym, segment, interval);
                        }
                        System.out.printf("    %-4s: %d symbols, %,d bars%n", interval, symbols.size(), totalBars);
                    }
                }
            }
            return 0;
        }
    }

    @Command(name = "quality", description = "Run data quality check for a symbol")
    static final class QualityCmd implements Callable<Integer> {
        @Parameters(index = "0") String symbol;
        @Option(names = "--interval", defaultValue = "1m") String interval;
        @Option(names = "--from", required = true) LocalDate from;
        @Option(names = "--to", required = true) LocalDate to;
        @Option(names = "--data-root", defaultValue = "data") Path dataRoot;

        @Override
        public Integer call() throws Exception {
            TradingCalendarStore calendar = new TradingCalendarStore();
            ParquetHistoricalDataStore store = new ParquetHistoricalDataStore(dataRoot, calendar);
            var report = store.qualityCheck(symbol, interval, from, to);
            System.out.println("=== Data Quality Report ===");
            System.out.println("  Symbol:       " + report.symbol());
            System.out.println("  Interval:     " + report.interval());
            System.out.println("  Range:        " + report.from() + " → " + report.to());
            System.out.println("  Expected:     " + report.expectedBars() + " trading days");
            System.out.println("  Actual:       " + report.actualBars() + " days with data");
            System.out.printf("  Completeness: %.1f%%%n", report.completenessPercent());
            System.out.println("  Missing:      " + report.missingBars() + " days");
            if (!report.gapDates().isEmpty()) {
                System.out.println("  Gap dates:");
                for (LocalDate gap : report.gapDates()) {
                    System.out.println("    " + gap);
                }
            }
            return 0;
        }
    }

    @Command(name = "intervals", description = "List available intervals for a symbol")
    static final class IntervalsCmd implements Callable<Integer> {
        @Parameters(index = "0") String symbol;
        @Option(names = "--segment", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--data-root", defaultValue = "data") Path dataRoot;

        @Override
        public Integer call() throws Exception {
            Path barsRoot = CanonicalPaths.barsRoot(dataRoot);
            try (CanonicalBarQuery query = new CanonicalBarQuery(barsRoot)) {
                List<String> intervals = query.availableIntervals(symbol, segment);
                if (intervals.isEmpty()) {
                    System.out.println("No data found for " + symbol + " in " + segment);
                    return 1;
                }
                System.out.println("Available intervals for " + symbol + ":");
                for (String interval : intervals) {
                    long count = query.countBars(symbol, segment, interval);
                    System.out.printf("  %-4s: %,d bars%n", interval, count);
                }
            }
            return 0;
        }
    }

    @Command(name = "resample", description = "Generate 5m/15m/1d parquet from 1m source data")
    static final class ResampleCmd implements Callable<Integer> {
        @Option(names = "--symbol") String symbol;
        @Option(names = "--all", description = "Resample all symbols") boolean all;
        @Option(names = "--segment", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--from", required = true) LocalDate from;
        @Option(names = "--to", required = true) LocalDate to;
        @Option(names = "--data-root", defaultValue = "data") Path dataRoot;

        @Override
        public Integer call() throws Exception {
            TradingCalendarStore calendar = new TradingCalendarStore();
            ParquetHistoricalDataStore store = new ParquetHistoricalDataStore(dataRoot, calendar);
            var writer = new com.tradej.historical.ingest.canonical.CanonicalBarWriter(dataRoot);
            MultiIntervalGenerator generator = new MultiIntervalGenerator(store, writer);

            if (all) {
                Path barsRoot = CanonicalPaths.barsRoot(dataRoot);
                try (CanonicalBarQuery query = new CanonicalBarQuery(barsRoot)) {
                    List<String> symbols = query.availableSymbols(segment, "1m");
                    System.out.println("Resampling " + symbols.size() + " symbols from 1m → 5m/15m/1d...");
                    generator.generateForAll(symbols, segment, from, to);
                }
            } else if (symbol != null) {
                System.out.println("Resampling " + symbol + " from 1m → 5m/15m/1d...");
                var result = generator.generateForSymbol(symbol, segment, from, to);
                System.out.printf("Done: read %,d 1m bars, wrote %,d derived bars%n",
                        result.sourceBarsRead(), result.derivedBarsWritten());
            } else {
                System.out.println("Specify --symbol RELIANCE or --all");
                return 1;
            }
            return 0;
        }
    }

    @Command(name = "query", description = "Query canonical parquet bars for a symbol")
    static final class QueryCmd implements Callable<Integer> {
        @Parameters(index = "0") String symbol;
        @Option(names = "--interval", defaultValue = "1m") String interval;
        @Option(names = "--segment", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--from", required = true) LocalDate from;
        @Option(names = "--to", required = true) LocalDate to;
        @Option(names = "--limit", defaultValue = "50") int limit;
        @Option(names = "--data-root", defaultValue = "data") Path dataRoot;

        @Override
        public Integer call() throws Exception {
            Path barsRoot = CanonicalPaths.barsRoot(dataRoot);
            try (CanonicalBarQuery query = new CanonicalBarQuery(barsRoot)) {
                long fromMs = from.atStartOfDay(java.time.ZoneId.of("Asia/Kolkata"))
                        .toInstant().toEpochMilli();
                long toMs = to.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata"))
                        .toInstant().toEpochMilli() - 1;
                List<Map<String, Object>> rows = query.queryBars(symbol, segment, interval, fromMs, toMs, limit);
                if (rows.isEmpty()) {
                    System.out.println("No bars found for " + symbol + " " + interval);
                    return 1;
                }
                System.out.printf("%-20s %10s %10s %10s %10s %12s %10s%n",
                        "Time", "Open", "High", "Low", "Close", "Volume", "OI");
                for (Map<String, Object> row : rows) {
                    long barMs = ((Number) row.get("bar_time_ms")).longValue();
                    String time = java.time.Instant.ofEpochMilli(barMs)
                            .atZone(java.time.ZoneId.of("Asia/Kolkata"))
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    System.out.printf("%-20s %10d %10d %10d %10d %12d %10d%n",
                            time,
                            row.get("open_paisa"), row.get("high_paisa"),
                            row.get("low_paisa"), row.get("close_paisa"),
                            row.get("volume"), row.get("oi"));
                }
                System.out.println(rows.size() + " bars shown");
            }
            return 0;
        }
    }

    @Command(name = "gaps", description = "Detect missing trading days in canonical parquet data")
    static final class GapsCmd implements Callable<Integer> {
        @Option(names = "--symbol") String symbol;
        @Option(names = "--all", description = "Check all symbols") boolean all;
        @Option(names = "--interval", defaultValue = "1m") String interval;
        @Option(names = "--segment", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--from", required = true) LocalDate from;
        @Option(names = "--to", required = true) LocalDate to;
        @Option(names = "--data-root", defaultValue = "data") Path dataRoot;

        @Override
        public Integer call() throws Exception {
            TradingCalendarStore calendar = new TradingCalendarStore();
            ParquetHistoricalDataStore store = new ParquetHistoricalDataStore(dataRoot, calendar);

            if (all) {
                Path barsRoot = CanonicalPaths.barsRoot(dataRoot);
                try (CanonicalBarQuery query = new CanonicalBarQuery(barsRoot)) {
                    List<String> symbols = query.availableSymbols(segment, interval);
                    System.out.println("Checking gaps for " + symbols.size() + " symbols (" + segment + " " + interval + ")");
                    System.out.printf("%-15s %8s %8s %8s %8.1f%%%n",
                            "Symbol", "Expected", "Actual", "Missing", "Complete");
                    int totalGaps = 0;
                    int symbolsWithGaps = 0;
                    for (String sym : symbols) {
                        var report = store.qualityCheck(sym, interval, from, to);
                        if (!report.isComplete()) {
                            symbolsWithGaps++;
                            totalGaps += report.missingBars();
                            System.out.printf("%-15s %8d %8d %8d %8.1f%%%n",
                                    sym, report.expectedBars(), report.actualBars(),
                                    report.missingBars(), report.completenessPercent());
                        }
                    }
                    System.out.println("\nSummary: " + symbolsWithGaps + "/" + symbols.size()
                            + " symbols have gaps, " + totalGaps + " total missing days");
                }
            } else if (symbol != null) {
                var report = store.qualityCheck(symbol, interval, from, to);
                System.out.println("=== Gap Report: " + symbol + " " + interval + " ===");
                System.out.println("  Range:        " + from + " → " + to);
                System.out.println("  Expected:     " + report.expectedBars() + " trading days");
                System.out.println("  Actual:       " + report.actualBars() + " days with data");
                System.out.println("  Missing:      " + report.missingBars() + " days");
                System.out.printf("  Completeness: %.1f%%%n", report.completenessPercent());
                if (!report.gapDates().isEmpty()) {
                    System.out.println("  Missing dates:");
                    for (LocalDate gap : report.gapDates()) {
                        System.out.println("    " + gap);
                    }
                }
            } else {
                System.out.println("Specify --symbol RELIANCE or --all");
                return 1;
            }
            return 0;
        }
    }

    @Command(name = "sync", description = "Sync missing equity data from broker API (requires Dhan credentials)")
    static final class SyncCmd implements Callable<Integer> {
        @ParentCommand CliParquetCommands parent;
        @Option(names = "--from", required = true) LocalDate from;
        @Option(names = "--to", required = true) LocalDate to;
        @Option(names = "--segment", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--data-root", defaultValue = "data/historical-equity") Path dataRoot;
        @Option(names = "--dry-run", description = "Show sync plan without executing") boolean dryRun;

        @Override
        public Integer call() throws Exception {
            TradingCalendarStore calendar = new TradingCalendarStore();
            var exchangeSegment = com.tradej.core.domain.value.ExchangeSegment.valueOf(segment);
            List<LocalDate> tradingDays = calendar.tradingDays(exchangeSegment, from, to);

            System.out.println("=== Sync Plan ===");
            System.out.println("  Segment:       " + segment);
            System.out.println("  Range:         " + from + " → " + to);
            System.out.println("  Trading days:  " + tradingDays.size());

            List<String> symbols;
            try {
                symbols = listSymbolsFromDisk(dataRoot.resolve("bars"));
            } catch (Exception ex) {
                System.out.println("ERROR: " + ex.getMessage());
                return 1;
            }
            System.out.println("  Symbols:       " + symbols.size());
            System.out.println("  API calls:     " + symbols.size() + " (90-day bulk windows)");
            System.out.println("  Est. time:     ~" + (symbols.size() / 5) + "s");

            if (dryRun) {
                System.out.println("\nDry run — no data fetched. Remove --dry-run to execute.");
                return 0;
            }

            System.out.println("\nStarting live sync via broker connection...");
            try {
                var brokerSession = parent.root.ops().context().broker();
                brokerSession.ensureCatalogLoaded();
                var marketData = brokerSession.connection().marketData();
                var resolver = brokerSession.connection().instruments();
                CliSyncRunner.run(marketData, resolver, dataRoot, from, to);
            } catch (Exception ex) {
                System.out.println("ERROR: Live sync requires a broker connection.");
                System.out.println("  Run with: --broker dhan --profile local");
                System.out.println("  Error: " + ex.getMessage());
                return 1;
            }
            return 0;
        }

        private static List<String> listSymbolsFromDisk(Path barsRoot) throws Exception {
            Path intervalDir = barsRoot.resolve("interval=1m");
            if (!Files.isDirectory(intervalDir)) {
                return List.of();
            }
            try (var stream = Files.list(intervalDir)) {
                return stream
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .filter(name -> name.startsWith("symbol="))
                        .map(name -> name.substring("symbol=".length()))
                        .sorted()
                        .toList();
            }
        }
    }
}
