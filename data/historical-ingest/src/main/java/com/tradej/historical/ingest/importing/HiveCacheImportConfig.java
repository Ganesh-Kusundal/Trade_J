package com.tradej.historical.ingest.importing;

import java.nio.file.Path;
import java.util.List;

public record HiveCacheImportConfig(
        Path sourceHive,
        Path targetRoot,
        Path universeCsv,
        Path industryParquet,
        String fromMonth,
        String toMonth,
        boolean force,
        List<String> symbolFilter,
        boolean importUniverse
) {
    public static final String DEFAULT_FROM_MONTH = "2020-01";

    public HiveCacheImportConfig {
        if (fromMonth == null || fromMonth.isBlank()) {
            fromMonth = DEFAULT_FROM_MONTH;
        }
        if (symbolFilter != null) {
            symbolFilter = symbolFilter.stream()
                    .map(s -> s.trim().toUpperCase())
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        } else {
            symbolFilter = List.of();
        }
    }
}
