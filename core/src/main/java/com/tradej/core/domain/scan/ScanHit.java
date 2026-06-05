package com.tradej.core.domain.scan;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.List;
import java.util.Map;

public record ScanHit(
        InstrumentKey instrumentKey,
        AssetClass assetClass,
        String underlying,
        double score,
        List<String> reasons,
        Map<String, Object> snapshotFields,
        boolean promoted
) {
    public ScanHit {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        snapshotFields = snapshotFields == null ? Map.of() : Map.copyOf(snapshotFields);
    }

    public String symbol() {
        return instrumentKey.symbol();
    }

    public ExchangeSegment exchangeSegment() {
        return instrumentKey.exchangeSegment();
    }
}
