package com.tradej.execution.depth;

import java.util.List;

public final class DepthAnalyticsEvents {

    private DepthAnalyticsEvents() {}

    public record DepthImbalanceSnapshot(
            String symbol, String segment,
            double topOfBookImbalance, double cumulativeImbalance,
            String trend, long timestampMs
    ) {}

    public record HeatmapChunk(
            String symbol, String segment,
            List<PriceBucket> priceBuckets, long windowStartMs
    ) {
        public record PriceBucket(long pricePaisa, long bidVol, long askVol) {}
    }

    public record IcebergSignal(
            String symbol, String segment,
            long pricePaisa, String side,
            double confidence, long estimatedTotalQty,
            long visibleQty, int refreshCount,
            long timestampMs
    ) {}

    public record AbsorptionSignal(
            String symbol, String segment,
            long pricePaisa, String side,
            double erosionRate, double breakoutProbability,
            boolean isCancellation, long timestampMs
    ) {}

    public record SRLevelsUpdate(
            String symbol, String segment,
            List<SRLevel> levels, long timestampMs
    ) {
        public record SRLevel(
                long pricePaisa, long pricePaisaHigh,
                String side, long totalVolume,
                double persistenceScore
        ) {}
    }
}
