package com.tradej.core.domain.scan;

import java.util.List;

public record ScanResult(
        ScanRun run,
        List<ScanHit> hits
) {
    public ScanResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
