package com.tradej.scanner.model;

public record RestScanSpec(
        int batchSize,
        boolean fetchOptionChainsOnCoarsePass
) {
    public RestScanSpec {
        if (batchSize <= 0) {
            batchSize = 50;
        }
    }
}
