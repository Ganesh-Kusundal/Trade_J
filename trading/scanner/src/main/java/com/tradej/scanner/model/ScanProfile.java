package com.tradej.scanner.model;

import com.tradej.scanner.criterion.ScanCriterion;

import java.util.List;

public record ScanProfile(
        String id,
        ScanMode mode,
        UniverseSpec universe,
        RestScanSpec rest,
        PromotionSpec promotion,
        List<ScanCriterion> criteria,
        boolean optionFinePassEnabled,
        OptionScanSpec optionScan
) {
    public ScanProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Scan profile id is required");
        }
        if (mode == null) {
            mode = ScanMode.REST_SNAPSHOT;
        }
        if (universe == null) {
            throw new IllegalArgumentException("Universe spec is required for profile " + id);
        }
        if (rest == null) {
            rest = new RestScanSpec(50, true);
        }
        if (promotion == null) {
            promotion = new PromotionSpec(0, null, 0, 0);
        }
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
    }

    public ScanProfile(
            String id,
            ScanMode mode,
            UniverseSpec universe,
            RestScanSpec rest,
            PromotionSpec promotion,
            List<ScanCriterion> criteria,
            boolean optionFinePassEnabled
    ) {
        this(id, mode, universe, rest, promotion, criteria, optionFinePassEnabled, null);
    }

    public boolean isOptionLiquidityProfile() {
        return optionScan != null;
    }
}
