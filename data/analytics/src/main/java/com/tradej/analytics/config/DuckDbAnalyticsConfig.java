package com.tradej.analytics.config;

import java.nio.file.Path;

public record DuckDbAnalyticsConfig(
        Path equityRoot,
        Path optionsWarehousePath,
        Path runtimeDbPath,
        boolean attachRuntimeDb,
        boolean sqlEnabled,
        int sqlMaxRows,
        long sqlMaxRuntimeMs
) {
    public static final int DEFAULT_SQL_MAX_ROWS = 10_000;
    public static final long DEFAULT_SQL_MAX_RUNTIME_MS = 30_000L;

    public DuckDbAnalyticsConfig {
        if (sqlMaxRows <= 0) {
            sqlMaxRows = DEFAULT_SQL_MAX_ROWS;
        }
        if (sqlMaxRuntimeMs <= 0) {
            sqlMaxRuntimeMs = DEFAULT_SQL_MAX_RUNTIME_MS;
        }
    }
}
