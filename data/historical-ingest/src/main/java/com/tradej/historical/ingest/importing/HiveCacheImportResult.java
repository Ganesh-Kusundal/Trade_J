package com.tradej.historical.ingest.importing;

import java.util.List;
import java.util.Map;

public record HiveCacheImportResult(
        int partitionsDiscovered,
        int symbolsProcessed,
        int filesWritten,
        int filesSkipped,
        int filesMissingSource,
        int filesFailed,
        long totalRowsWritten,
        long elapsedMs,
        List<String> failedDetails,
        Map<String, Integer> missingMonthsBySymbol
) {
}
