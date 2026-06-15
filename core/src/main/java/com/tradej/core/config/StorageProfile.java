package com.tradej.core.config;

import java.nio.file.Path;

public record StorageProfile(
        Path chroniclePath,
        Path duckdbPath
) {
    public static StorageProfile defaults() {
        return new StorageProfile(
                Path.of("runtime/chronicle"),
                Path.of("runtime/tradej.duckdb")
        );
    }
}
