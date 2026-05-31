package com.tradej.institutional.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record InstitutionalScanResult(
        String scanDate,
        String scanTime,
        String selectionMode,
        List<ScoredBar> candidates,
        Map<String, String> provenance
) {
}
