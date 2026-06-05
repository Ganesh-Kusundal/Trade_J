package com.tradej.scanner.model;

import com.tradej.core.domain.scan.AssetClass;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.List;

public record UniverseSpec(
        List<ExchangeSegment> segments,
        List<AssetClass> assetClasses,
        List<String> underlyings,
        String indexConstituentsFile,
        long minLotSize
) {
    public UniverseSpec {
        segments = segments == null ? List.of() : List.copyOf(segments);
        assetClasses = assetClasses == null ? List.of(AssetClass.EQUITY) : List.copyOf(assetClasses);
        underlyings = underlyings == null ? List.of() : List.copyOf(underlyings);
    }
}
