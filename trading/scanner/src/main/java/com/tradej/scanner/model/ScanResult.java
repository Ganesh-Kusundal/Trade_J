package com.tradej.scanner.model;

import java.util.List;

public record ScanResult(
        ScanRun run,
        List<ScanHit> hits
) {
    public ScanResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
