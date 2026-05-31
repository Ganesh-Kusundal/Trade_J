package com.tradej.cli.download;

import com.tradej.historical.ingest.importing.HiveCacheEquityImporter;
import com.tradej.historical.ingest.importing.HiveCacheImportConfig;
import com.tradej.historical.ingest.importing.HiveCacheImportResult;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CliEquityImportSupport {

    private CliEquityImportSupport() {
    }

    public static HiveCacheImportResult importHive(
            String sourceHive,
            String universeCsv,
            String industryParquet,
            String rootPath,
            String fromMonth,
            String toMonth,
            boolean force,
            String symbolsCsv,
            boolean skipUniverseImport
    ) throws Exception {
        validateRequired(sourceHive, "--source-hive");
        validateRequired(universeCsv, "--universe-csv");
        validateRequired(industryParquet, "--industry-parquet");

        HiveCacheImportConfig config = new HiveCacheImportConfig(
                Path.of(sourceHive),
                Path.of(rootPath == null || rootPath.isBlank() ? HistoricalEquityPaths.DEFAULT_ROOT : rootPath),
                Path.of(universeCsv),
                Path.of(industryParquet),
                fromMonth == null || fromMonth.isBlank() ? HiveCacheImportConfig.DEFAULT_FROM_MONTH : fromMonth,
                toMonth,
                force,
                parseSymbols(symbolsCsv),
                !skipUniverseImport
        );
        return new HiveCacheEquityImporter().importHive(config);
    }

    public static Map<String, Object> toResponseMap(HiveCacheImportResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partitionsDiscovered", result.partitionsDiscovered());
        body.put("symbolsProcessed", result.symbolsProcessed());
        body.put("filesWritten", result.filesWritten());
        body.put("filesSkipped", result.filesSkipped());
        body.put("filesMissingSource", result.filesMissingSource());
        body.put("filesFailed", result.filesFailed());
        body.put("totalRowsWritten", result.totalRowsWritten());
        body.put("elapsedMs", result.elapsedMs());
        body.put("failedDetails", result.failedDetails());
        body.put("missingMonthsBySymbol", result.missingMonthsBySymbol());
        return body;
    }

    private static List<String> parseSymbols(String symbolsCsv) {
        if (symbolsCsv == null || symbolsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(symbolsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static void validateRequired(String value, String flag) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option " + flag);
        }
    }
}
