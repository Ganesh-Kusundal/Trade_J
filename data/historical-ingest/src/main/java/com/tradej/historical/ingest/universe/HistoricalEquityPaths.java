package com.tradej.historical.ingest.universe;

import com.tradej.core.infrastructure.WorkspacePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class HistoricalEquityPaths {

    public static final String DEFAULT_ROOT = "data/historical-equity";
    private static final List<String> PARTITION_PROBE_SYMBOLS = List.of(
            "SBIN", "RELIANCE", "INFY", "TCS", "HDFCBANK", "ICICIBANK"
    );

    private HistoricalEquityPaths() {
    }

    public static Path root(Path configuredRoot) {
        return root(WorkspacePaths.fromSystemProperty(), configuredRoot);
    }

    public static Path root(WorkspacePaths workspacePaths, Path configuredRoot) {
        Path candidate = workspacePaths.historicalEquityRoot(
                configuredRoot == null || configuredRoot.toString().isBlank()
                        ? DEFAULT_ROOT
                        : configuredRoot.toString());
        if (hasWarehouseData(candidate)) {
            return candidate;
        }
        return candidate;
    }

    private static boolean hasWarehouseData(Path root) {
        return Files.isDirectory(root)
                && (Files.exists(symbolsParquet(root)) || Files.isDirectory(barsDir(root, "interval=1m")));
    }

    public static Path universeDir(Path root) {
        return root.resolve("universe");
    }

    public static Path symbolsParquet(Path root) {
        return universeDir(root).resolve("nifty500_symbols.parquet");
    }

    public static Path industryParquet(Path root) {
        return universeDir(root).resolve("nifty500_industry.parquet");
    }

    public static Path barsDir(Path root, String intervalFolder) {
        return root.resolve("bars").resolve(intervalFolder);
    }

    public static Path symbolBarDir(Path root, String intervalFolder, String symbol) {
        return barsDir(root, intervalFolder).resolve("symbol=" + symbol);
    }

    public static Path metaDatabase(Path root) {
        return root.resolve("_meta").resolve("jobs.duckdb");
    }

    public static Path partitionGlob(Path root, String intervalFolder, String partitionFile) {
        return barsDir(root, intervalFolder).resolve("symbol=*").resolve(partitionFile);
    }

    /**
     * Latest hive month file under {@code bars/interval=1m} for fast latest-day probes.
     */
    public static Optional<String> latestHivePartitionFile(Path root, String intervalFolder) {
        for (String symbol : PARTITION_PROBE_SYMBOLS) {
            Optional<String> partition = latestHivePartitionInDir(symbolBarDir(root, intervalFolder, symbol));
            if (partition.isPresent()) {
                return partition;
            }
        }

        Path barsDir = barsDir(root, intervalFolder);
        if (!Files.isDirectory(barsDir)) {
            return Optional.empty();
        }
        try (Stream<Path> symbolDirs = Files.list(barsDir)) {
            return symbolDirs
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("symbol="))
                    .map(HistoricalEquityPaths::latestHivePartitionInDir)
                    .flatMap(Optional::stream)
                    .max(String::compareTo);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> latestHivePartitionInDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("part-hive-") && name.endsWith(".parquet"))
                    .max(String::compareTo);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }
}
