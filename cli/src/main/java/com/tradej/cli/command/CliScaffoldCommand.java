package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;

/**
 * Generates boilerplate for new platform extensions (strategies, scanners, indicators, analytics).
 *
 * <p>Usage:
 * <pre>
 *   tradej scaffold strategy --name HalfTrend --type candle
 *   tradej scaffold scanner --name RelativeStrength
 *   tradej scaffold indicator --name BollingerBands
 *   tradej scaffold analytics --name SectorRotation
 * </pre>
 */
@Command(name = "scaffold", description = "Generate boilerplate for new platform extensions",
        subcommands = {
                CliScaffoldCommand.StrategySubcommand.class,
                CliScaffoldCommand.ScannerSubcommand.class,
                CliScaffoldCommand.IndicatorSubcommand.class,
                CliScaffoldCommand.AnalyticsSubcommand.class
        })
public final class CliScaffoldCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Override
    public Integer call() {
        System.out.println(Ansi.bold("  tradej scaffold — Generate platform extension boilerplate\n"));
        System.out.println("  Subcommands:");
        System.out.println("    strategy    Generate a new GraphStrategyPlugin");
        System.out.println("    scanner     Generate a new ScanCriterion");
        System.out.println("    indicator   Generate a new IndicatorProvider");
        System.out.println("    analytics   Generate a new AnalyticsProvider");
        System.out.println("\n  Example: tradej scaffold strategy --name HalfTrend --type candle");
        return 0;
    }

    // ── Strategy ─────────────────────────────────────────────────

    @Command(name = "strategy", description = "Generate a new GraphStrategyPlugin")
    static final class StrategySubcommand implements Callable<Integer> {

        @ParentCommand CliScaffoldCommand parent;

        @Option(names = "--name", required = true, description = "Strategy class name (e.g., HalfTrend)")
        String name;

        @Option(names = "--type", defaultValue = "candle", description = "Event type: tick or candle (default: candle)")
        String type;

        @Option(names = "--package", defaultValue = "com.tradej.custom.strategy", description = "Java package")
        String pkg;

        @Option(names = "--output-dir", defaultValue = ".", description = "Output directory")
        String outputDir;

        @Override
        public Integer call() throws IOException {
            String eventClass = "tick".equalsIgnoreCase(type) ? "MarketTickEvent" : "CandleClosed";
            String eventImport = "tick".equalsIgnoreCase(type)
                    ? "com.tradej.core.domain.event.MarketTickEvent"
                    : "com.tradej.core.domain.event.CandleClosed";

            String source = """
                    package %s;

                    import com.tradej.core.domain.event.DomainEvent;
                    import com.tradej.core.domain.event.EventMetadata;
                    import com.tradej.core.domain.event.SignalGenerated;
                    import %s;
                    import com.tradej.core.domain.value.Side;
                    import com.tradej.strategy.api.GraphStrategyPlugin;
                    import org.slf4j.Logger;
                    import org.slf4j.LoggerFactory;

                    import java.util.List;
                    import java.util.Map;
                    import java.util.Optional;
                    import java.util.UUID;

                    /**
                     * TODO: Describe your strategy logic here.
                     */
                    public final class %s implements GraphStrategyPlugin {

                        private static final Logger log = LoggerFactory.getLogger(%s.class);

                        @Override
                        public String name() {
                            return "%s";
                        }

                        @Override
                        public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                            return List.of(%s.class);
                        }

                        @Override
                        public Optional<SignalGenerated> onEvent(DomainEvent event) {
                            if (!(event instanceof %s e)) {
                                return Optional.empty();
                            }

                            // TODO: Implement your strategy logic here.
                            // Return Optional.of(signal) when a signal is triggered,
                            // or Optional.empty() otherwise.

                            return Optional.empty();
                        }

                        @Override
                        public void onStart() {
                            log.info("Strategy '%s' started");
                        }

                        @Override
                        public void onStop() {
                            log.info("Strategy '%s' stopped");
                        }
                    }
                    """.formatted(pkg, eventImport, name, name,
                    name.toLowerCase().replace(" ", "-"),
                    eventClass, eventClass, name, name);

            Path file = writeSource(outputDir, pkg, name + ".java", source);

            System.out.println(Ansi.bold("\n  Strategy generated!\n"));
            System.out.println("  File: " + file);
            System.out.println("\n  Next steps:");
            System.out.println("  1. Implement your strategy logic in onEvent()");
            System.out.println("  2. Register in META-INF/services/com.tradej.strategy.api.GraphStrategyPlugin:");
            System.out.println("     " + pkg + "." + name);
            System.out.println("  3. Write tests using StrategyTestHarness");
            System.out.println("  4. Build and verify: tradej plugins");
            return 0;
        }
    }

    // ── Scanner ──────────────────────────────────────────────────

    @Command(name = "scanner", description = "Generate a new ScanCriterion")
    static final class ScannerSubcommand implements Callable<Integer> {

        @ParentCommand CliScaffoldCommand parent;

        @Option(names = "--name", required = true, description = "Criterion class name (e.g., RelativeStrength)")
        String name;

        @Option(names = "--package", defaultValue = "com.tradej.custom.scanner", description = "Java package")
        String pkg;

        @Option(names = "--output-dir", defaultValue = ".", description = "Output directory")
        String outputDir;

        @Override
        public Integer call() throws IOException {
            String typeName = name.toLowerCase().replace(" ", "-");

            String source = """
                    package %s;

                    import com.tradej.scanner.criterion.ScanCriterion;
                    import com.tradej.scanner.model.ScanContext;

                    /**
                     * TODO: Describe your scanner criterion here.
                     */
                    public final class %s implements ScanCriterion {

                        @Override
                        public String type() {
                            return "%s";
                        }

                        @Override
                        public boolean matches(ScanContext context) {
                            if (!context.hasValidQuote()) {
                                return false;
                            }

                            // TODO: Implement your matching logic here.
                            // Return true if this symbol meets your criterion.

                            return false;
                        }

                        @Override
                        public double score(ScanContext context) {
                            // TODO: Return a score (higher = stronger signal).
                            return 0.0;
                        }

                        @Override
                        public String reason(ScanContext context) {
                            return type() + "=" + score(context);
                        }
                    }
                    """.formatted(pkg, name, typeName);

            Path file = writeSource(outputDir, pkg, name + ".java", source);

            System.out.println(Ansi.bold("\n  Scanner criterion generated!\n"));
            System.out.println("  File: " + file);
            System.out.println("\n  Next steps:");
            System.out.println("  1. Implement matches() and score() logic");
            System.out.println("  2. Create a ScannerProvider that includes this criterion");
            System.out.println("  3. Register in META-INF/services/com.tradej.scanner.spi.ScannerProvider");
            System.out.println("  4. Write tests using ScannerTestHarness");
            return 0;
        }
    }

    // ── Indicator ────────────────────────────────────────────────

    @Command(name = "indicator", description = "Generate a new IndicatorProvider")
    static final class IndicatorSubcommand implements Callable<Integer> {

        @ParentCommand CliScaffoldCommand parent;

        @Option(names = "--name", required = true, description = "Indicator class name (e.g., BollingerBands)")
        String name;

        @Option(names = "--package", defaultValue = "com.tradej.custom.indicator", description = "Java package")
        String pkg;

        @Option(names = "--output-dir", defaultValue = ".", description = "Output directory")
        String outputDir;

        @Override
        public Integer call() throws IOException {
            String indicatorName = name.toLowerCase().replace(" ", "-");

            String source = """
                    package %s;

                    import com.tradej.core.domain.model.Candle;
                    import com.tradej.indicators.spi.IndicatorProvider;

                    import java.util.List;

                    /**
                     * TODO: Describe your indicator here.
                     */
                    public final class %s implements IndicatorProvider {

                        @Override
                        public String name() {
                            return "%s";
                        }

                        @Override
                        public String displayName() {
                            return "%s";
                        }

                        @Override
                        public List<Double> calculate(List<Candle> candles) {
                            // TODO: Implement your indicator calculation here.
                            // Return a list of values, one per candle (or empty for insufficient data).
                            return List.of();
                        }
                    }
                    """.formatted(pkg, name, indicatorName, name);

            Path file = writeSource(outputDir, pkg, name + ".java", source);

            System.out.println(Ansi.bold("\n  Indicator generated!\n"));
            System.out.println("  File: " + file);
            System.out.println("\n  Next steps:");
            System.out.println("  1. Implement calculate() logic");
            System.out.println("  2. Register in META-INF/services/com.tradej.indicators.spi.IndicatorProvider:");
            System.out.println("     " + pkg + "." + name);
            System.out.println("  3. Verify: tradej plugins");
            return 0;
        }
    }

    // ── Analytics ────────────────────────────────────────────────

    @Command(name = "analytics", description = "Generate a new AnalyticsProvider")
    static final class AnalyticsSubcommand implements Callable<Integer> {

        @ParentCommand CliScaffoldCommand parent;

        @Option(names = "--name", required = true, description = "Analytics class name (e.g., SectorRotation)")
        String name;

        @Option(names = "--package", defaultValue = "com.tradej.custom.analytics", description = "Java package")
        String pkg;

        @Option(names = "--output-dir", defaultValue = ".", description = "Output directory")
        String outputDir;

        @Override
        public Integer call() throws IOException {
            String analyticsName = name.toLowerCase().replace(" ", "-");

            String source = """
                    package %s;

                    import com.tradej.analytics.spi.AnalyticsProvider;

                    import java.util.List;
                    import java.util.Map;

                    /**
                     * TODO: Describe your analytics module here.
                     */
                    public final class %s implements AnalyticsProvider {

                        @Override
                        public String name() {
                            return "%s";
                        }

                        @Override
                        public String displayName() {
                            return "%s";
                        }

                        @Override
                        public List<String> capabilities() {
                            return List.of(
                                    // TODO: List your analytics capabilities
                                    "%s"
                            );
                        }

                        @Override
                        public Map<String, String> metadata() {
                            return Map.of(
                                    "engine", "DuckDB",
                                    "description", "TODO: Describe your analytics module"
                            );
                        }
                    }
                    """.formatted(pkg, name, analyticsName, name, analyticsName);

            Path file = writeSource(outputDir, pkg, name + ".java", source);

            System.out.println(Ansi.bold("\n  Analytics module generated!\n"));
            System.out.println("  File: " + file);
            System.out.println("\n  Next steps:");
            System.out.println("  1. Implement your analytics queries");
            System.out.println("  2. Register in META-INF/services/com.tradej.analytics.spi.AnalyticsProvider:");
            System.out.println("     " + pkg + "." + name);
            System.out.println("  3. Verify: tradej plugins");
            return 0;
        }
    }

    // ── Shared utility ───────────────────────────────────────────

    private static Path writeSource(String outputDir, String pkg, String fileName, String source) throws IOException {
        Path dir = Path.of(outputDir, pkg.replace('.', '/'));
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.writeString(file, source, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }
}
