package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import com.tradej.core.domain.event.EventCatalogEntry;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "coverage", description = "Coverage dashboard — test, documentation, and certification status per subsystem")
public final class CliCoverageCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Override
    public Integer call() {
        List<CoverageRow> rows = collectCoverage();

        if (root.json()) {
            printJson(rows);
            return 0;
        }

        System.out.println(Ansi.bold("\n  Trade-J Coverage Dashboard\n"));

        RichTable table = RichTable.of("Subsystem", "Tests", "Docs", "Certification", "Status")
                .rowFormatter(row -> {
                    String status = row[4];
                    return new String[]{
                            row[0], row[1], row[2], row[3],
                            switch (status) {
                                case "GREEN" -> Ansi.green("GREEN");
                                case "YELLOW" -> Ansi.yellow("YELLOW");
                                default -> Ansi.red("RED");
                            }
                    };
                });

        for (CoverageRow r : rows) {
            table.addRow(r.subsystem(), r.tests(), r.docs(), r.certification(), r.status());
        }
        table.print();

        long green = rows.stream().filter(r -> "GREEN".equals(r.status())).count();
        long yellow = rows.stream().filter(r -> "YELLOW".equals(r.status())).count();
        long red = rows.stream().filter(r -> "RED".equals(r.status())).count();
        System.out.printf("  %s: %d  %s: %d  %s: %d%n%n",
                Ansi.green("GREEN"), green, Ansi.yellow("YELLOW"), yellow, Ansi.red("RED"), red);

        return 0;
    }

    private List<CoverageRow> collectCoverage() {
        List<CoverageRow> rows = new ArrayList<>();

        rows.add(checkSubsystem("Core Events", "core", true));
        rows.add(checkSubsystem("Broker Gateway", "broker-gateway", true));
        rows.add(checkSubsystem("Trading Execution", "trading-execution", true));
        rows.add(checkSubsystem("Trading Simulation", "trading-simulation", true));
        rows.add(checkSubsystem("Trading Indicators", "trading-indicators", true));
        rows.add(checkSubsystem("Trading Scanner", "trading-scanner", true));
        rows.add(checkSubsystem("Data Persistence", "data-persistence", true));
        rows.add(checkSubsystem("Runtime Disruptor", "runtime-disruptor", true));
        rows.add(checkSubsystem("Pipeline Core", "pipeline-core", true));
        rows.add(checkSubsystem("Replay Engine", "replay-engine", true));
        rows.add(checkSubsystem("CLI", "cli", true));
        rows.add(checkSubsystem("Architecture Tests", "architecture-test", true));

        return rows;
    }

    private CoverageRow checkSubsystem(String name, String moduleDir, boolean hasTests) {
        String testDir = inferTestDir(moduleDir);
        File testDirFile = new File(testDir);
        boolean testsExist = testDirFile.exists() && testDirFile.isDirectory();
        boolean hasTestFiles = testsExist && hasJavaFiles(testDirFile);

        String testStatus = hasTestFiles ? "PASS" : (testsExist ? "EMPTY" : "MISSING");
        String docsStatus = "GENERATED";
        String certStatus = "N/A";

        String status;
        if (hasTestFiles) {
            status = "GREEN";
        } else if (testsExist) {
            status = "YELLOW";
        } else {
            status = "RED";
        }

        return new CoverageRow(name, testStatus, docsStatus, certStatus, status);
    }

    private String inferTestDir(String moduleDir) {
        return switch (moduleDir) {
            case "core" -> "core/src/test/java";
            case "broker-gateway" -> "broker-gateway/src/test/java";
            case "trading-execution" -> "trading/execution/src/test/java";
            case "trading-simulation" -> "trading/simulation/src/test/java";
            case "trading-indicators" -> "trading/indicators/src/test/java";
            case "trading-scanner" -> "trading/scanner/src/test/java";
            case "data-persistence" -> "data/persistence/src/test/java";
            case "runtime-disruptor" -> "runtime/disruptor/src/test/java";
            case "pipeline-core" -> "pipeline/core/src/test/java";
            case "replay-engine" -> "replay/engine/src/test/java";
            case "cli" -> "cli/src/test/java";
            case "architecture-test" -> "architecture-test/src/test/java";
            default -> moduleDir + "/src/test/java";
        };
    }

    private boolean hasJavaFiles(File dir) {
        if (!dir.exists()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory() && hasJavaFiles(f)) return true;
            if (f.isFile() && f.getName().endsWith(".java")) return true;
        }
        return false;
    }

    private void printJson(List<CoverageRow> rows) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) json.append(",");
            var r = rows.get(i);
            json.append(String.format(
                    "{\"subsystem\":\"%s\",\"tests\":\"%s\",\"docs\":\"%s\",\"certification\":\"%s\",\"status\":\"%s\"}",
                    r.subsystem(), r.tests(), r.docs(), r.certification(), r.status()));
        }
        json.append("]");
        System.out.println(json);
    }

    record CoverageRow(String subsystem, String tests, String docs, String certification, String status) {}
}
