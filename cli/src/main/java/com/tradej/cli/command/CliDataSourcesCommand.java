package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "datasources", description = "List known data sources — DuckDB stores, Chronicle paths, parquet roots")
public final class CliDataSourcesCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    private static final String[][] KNOWN_SOURCES = {
            {"DuckDB Event Store", "runtime-dev/events.duckdb", "event persistence"},
            {"DuckDB Feature Store", "runtime-dev/features.duckdb", "time-series features"},
            {"DuckDB Historical Warehouse", "runtime-dev/historical.duckdb", "historical data"},
            {"Chronicle Audit Log", "runtime-dev/chronicle", "append-only audit trail"},
            {"Equity Historical Root", "data/historical-equity", "parquet partitioned equity bars"},
            {"Pipeline Graph Store", "runtime-dev/pipeline.duckdb", "pipeline graph versions"},
            {"Scan Results Store", "runtime-dev/scan.duckdb", "scanner results"},
    };

    @Override
    public Integer call() {
        List<DataSourceEntry> entries = new ArrayList<>();

        for (String[] src : KNOWN_SOURCES) {
            File file = new File(src[1]);
            String status;
            String detail;
            if (file.exists()) {
                if (file.isDirectory()) {
                    int count = countFiles(file);
                    status = "AVAILABLE";
                    detail = count + " files";
                } else {
                    status = "AVAILABLE";
                    detail = formatSize(file.length());
                }
            } else {
                status = "NOT FOUND";
                detail = "created on first use";
            }
            entries.add(new DataSourceEntry(src[0], src[1], src[2], status, detail));
        }

        if (root.json()) {
            printJson(entries);
            return 0;
        }

        System.out.println(Ansi.bold("\n  Data Sources\n"));

        RichTable table = RichTable.of("Source", "Path", "Purpose", "Status", "Detail")
                .rowFormatter(row -> {
                    String status = row[3];
                    return new String[]{
                            row[0], row[1], row[2],
                            "AVAILABLE".equals(status) ? Ansi.green(status) : Ansi.dim(status),
                            row[4]
                    };
                });

        for (DataSourceEntry e : entries) {
            table.addRow(e.name(), e.path(), e.purpose(), e.status(), e.detail());
        }
        table.print();

        long available = entries.stream().filter(e -> "AVAILABLE".equals(e.status())).count();
        System.out.println(Ansi.dim("  " + available + "/" + entries.size() + " data sources available"));
        return 0;
    }

    private static int countFiles(File dir) {
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void printJson(List<DataSourceEntry> entries) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) json.append(",");
            var e = entries.get(i);
            json.append(String.format(
                    "{\"name\":\"%s\",\"path\":\"%s\",\"purpose\":\"%s\",\"status\":\"%s\",\"detail\":\"%s\"}",
                    e.name(), e.path(), e.purpose(), e.status(), e.detail()));
        }
        json.append("]");
        System.out.println(json);
    }

    record DataSourceEntry(String name, String path, String purpose, String status, String detail) {}
}
