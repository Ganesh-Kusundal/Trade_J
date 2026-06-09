package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Command(name = "regression", description = "Regression catalog — maps test files to modules and coverage areas")
public final class CliRegressionCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Option(names = "--filter", description = "Filter by module or test name")
    String filter;

    @Option(names = "--summary", description = "Show summary only")
    boolean summary;

    @Option(names = "--git", description = "Show last git commit per test file")
    boolean git;

    private static final Pattern TEST_CLASS_PATTERN = Pattern.compile(
            "class\\s+(\\w+)\\s");
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "@Tag\\(\"(\\w+)\"\\)");

    @Override
    public Integer call() {
        List<RegressionEntry> entries = scanTestFiles();

        if (filter != null && !filter.isBlank()) {
            String lowerFilter = filter.toLowerCase();
            entries = entries.stream()
                    .filter(e -> e.module().toLowerCase().contains(lowerFilter)
                            || e.testClass().toLowerCase().contains(lowerFilter)
                            || e.tag().toLowerCase().contains(lowerFilter))
                    .toList();
        }

        if (root.json()) {
            printJson(entries);
            return 0;
        }

        if (summary) {
            printSummary(entries);
        } else if (git) {
            printWithGit(entries);
        } else {
            printTable(entries);
        }

        return 0;
    }

    static List<RegressionEntry> scanTestFiles() {
        List<RegressionEntry> entries = new ArrayList<>();
        String workspaceRoot = System.getProperty("trade.workspace.root", ".");

        String[][] moduleTestDirs = {
                {"core", "core/src/test/java"},
                {"broker-gateway", "broker-gateway/src/test/java"},
                {"broker-dhan", "broker/dhan/src/test/java"},
                {"broker-upstox", "broker/upstox/src/test/java"},
                {"broker-icici", "broker/icici/src/test/java"},
                {"broker-core", "broker/core/src/test/java"},
                {"trading-execution", "trading/execution/src/test/java"},
                {"trading-simulation", "trading/simulation/src/test/java"},
                {"trading-strategy", "trading/strategy/src/test/java"},
                {"trading-scanner", "trading/scanner/src/test/java"},
                {"trading-indicators", "trading/indicators/src/test/java"},
                {"trading-options-analytics", "trading/options-analytics/src/test/java"},
                {"data-persistence", "data/persistence/src/test/java"},
                {"data-feature-store", "data/feature-store/src/test/java"},
                {"data-historical-ingest", "data/historical-ingest/src/test/java"},
                {"data-analytics", "data/analytics/src/test/java"},
                {"runtime-disruptor", "runtime/disruptor/src/test/java"},
                {"runtime-hotpath", "runtime/hotpath/src/test/java"},
                {"pipeline-core", "pipeline/core/src/test/java"},
                {"pipeline-runtime", "pipeline/runtime/src/test/java"},
                {"replay-engine", "replay/engine/src/test/java"},
                {"cli", "cli/src/test/java"},
                {"gateway", "gateway/src/test/java"},
                {"composition", "composition/src/test/java"},
                {"architecture-test", "architecture-test/src/test/java"},
                {"app", "app/src/test/java"},
        };

        for (String[] module : moduleTestDirs) {
            Path testDir = Path.of(workspaceRoot, module[1]);
            if (!Files.exists(testDir)) continue;
            try (Stream<Path> files = Files.walk(testDir)) {
                files.filter(f -> f.toString().endsWith("Test.java") || f.toString().endsWith("Tests.java"))
                        .forEach(f -> {
                            String tag = extractTag(f);
                            String className = f.getFileName().toString().replace(".java", "");
                            String category = categorizeTest(className, tag);
                            entries.add(new RegressionEntry(module[0], className, tag, category,
                                    module[1] + "/" + testDir.relativize(f)));
                        });
            } catch (IOException e) {
                // skip module
            }
        }
        return entries;
    }

    private static String extractTag(Path file) {
        try {
            String content = Files.readString(file);
            Matcher matcher = TAG_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IOException e) {
            // ignore
        }
        return "unit";
    }

    private void printWithGit(List<RegressionEntry> entries) {
        String workspaceRoot = System.getProperty("trade.workspace.root", ".");
        System.out.println(Ansi.bold("\n  Regression Test Catalog (with Git History)\n"));

        RichTable table = RichTable.of("Module", "Test Class", "Category", "Last Commit", "Date", "Fix?")
                .rowFormatter(row -> {
                    String isFix = row[5];
                    return new String[]{
                            row[0], row[1], row[2], row[3], row[4],
                            "YES".equals(isFix) ? Ansi.yellow(isFix) : Ansi.dim(isFix)
                    };
                });

        for (RegressionEntry e : entries) {
            String[] gitInfo = getLastCommit(workspaceRoot, e.path());
            table.addRow(e.module(), e.testClass(), e.category(),
                    gitInfo[0], gitInfo[1], gitInfo[2]);
        }
        table.print();

        System.out.println(Ansi.dim("  Total: " + entries.size() + " test classes"));
    }

    static String[] getLastCommit(String workspaceRoot, String filePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "log", "-1", "--format=%h|%ad|%s",
                    "--date=short", "--", filePath);
            pb.directory(new java.io.File(workspaceRoot));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();
            if (!output.isEmpty()) {
                String[] parts = output.split("\\|", 3);
                if (parts.length >= 3) {
                    boolean isFix = parts[2].toLowerCase().contains("fix")
                            || parts[2].toLowerCase().contains("bugfix")
                            || parts[2].toLowerCase().contains("patch");
                    return new String[]{parts[0], parts[1], isFix ? "YES" : "no"};
                }
            }
        } catch (Exception e) {
            // git not available
        }
        return new String[]{"—", "—", "—"};
    }

    static String categorizeTest(String className, String tag) {
        if (className.contains("Architecture") || className.contains("SpringFree")) return "architecture";
        if (className.contains("Certification") || className.contains("E2E")) return "certification";
        if (className.contains("Concurrency") || className.contains("Stress") || className.contains("Chaos")) {
            return "concurrency";
        }
        if (className.contains("Integration") || "integration".equals(tag)) return "integration";
        if (className.contains("Component") || "component".equals(tag)) return "component";
        if (className.contains("Regression") || className.contains("Preflight")) return "regression";
        if (className.contains("Parity") || className.contains("Replay")) return "replay";
        if (className.contains("Simulation") || className.contains("Matching")) return "simulation";
        if (className.contains("Broker") || "broker-rest".equals(tag)) return "broker";
        if (className.contains("Indicator")) return "indicator";
        if (className.contains("Risk") || className.contains("Command")) return "risk";
        return "unit";
    }

    private void printTable(List<RegressionEntry> entries) {
        System.out.println(Ansi.bold("\n  Regression Test Catalog\n"));

        RichTable table = RichTable.of("Module", "Test Class", "Tag", "Category")
                .rowFormatter(row -> {
                    String cat = row[3];
                    return new String[]{
                            row[0], row[1], row[2],
                            switch (cat) {
                                case "architecture" -> Ansi.magenta(cat);
                                case "certification" -> Ansi.green(cat);
                                case "concurrency" -> Ansi.yellow(cat);
                                case "integration" -> Ansi.cyan(cat);
                                default -> Ansi.dim(cat);
                            }
                    };
                });

        for (RegressionEntry e : entries) {
            table.addRow(e.module(), e.testClass(), e.tag(), e.category());
        }
        table.print();

        System.out.println(Ansi.dim("  Total: " + entries.size() + " test classes"));
    }

    private void printSummary(List<RegressionEntry> entries) {
        System.out.println(Ansi.bold("\n  Regression Test Summary\n"));

        var byModule = entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(RegressionEntry::module,
                        java.util.stream.Collectors.counting()));
        var byCategory = entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(RegressionEntry::category,
                        java.util.stream.Collectors.counting()));

        RichTable moduleTable = RichTable.of("Module", "Test Count");
        byModule.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> moduleTable.addRow(e.getKey(), String.valueOf(e.getValue())));
        moduleTable.print();

        System.out.println();
        RichTable catTable = RichTable.of("Category", "Count");
        byCategory.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> catTable.addRow(e.getKey(), String.valueOf(e.getValue())));
        catTable.print();

        System.out.println(Ansi.dim("\n  Total: " + entries.size() + " test classes across "
                + byModule.size() + " modules"));
    }

    private void printJson(List<RegressionEntry> entries) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) json.append(",");
            var e = entries.get(i);
            json.append(String.format(
                    "{\"module\":\"%s\",\"testClass\":\"%s\",\"tag\":\"%s\",\"category\":\"%s\"}",
                    e.module(), e.testClass(), e.tag(), e.category()));
        }
        json.append("]");
        System.out.println(json);
    }

    record RegressionEntry(String module, String testClass, String tag, String category, String path) {}
}
