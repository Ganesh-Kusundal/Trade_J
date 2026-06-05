package com.tradej.scanner.engine;

import com.tradej.core.domain.scan.ScanHit;

import java.util.Comparator;
import java.util.List;

public final class ScanResultRanker {
    private ScanResultRanker() {
    }

    public static List<ScanHit> rank(List<ScanHit> hits, int topN) {
        List<ScanHit> sorted = hits.stream()
                .sorted(Comparator
                        .comparingDouble(ScanHit::score).reversed()
                        .thenComparing(ScanHit::symbol))
                .toList();
        if (topN <= 0 || topN >= sorted.size()) {
            return sorted;
        }
        return List.copyOf(sorted.subList(0, topN));
    }
}
