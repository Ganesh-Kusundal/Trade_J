package com.tradej.scanner.model;

import com.tradej.core.domain.value.FeedMode;

public record PromotionSpec(
        int topN,
        FeedMode feedMode,
        int maxConcurrentPromotions,
        int promotionTtlMinutes
) {
    public PromotionSpec {
        if (topN < 0) {
            throw new IllegalArgumentException("topN must be >= 0");
        }
        if (feedMode == null) {
            feedMode = FeedMode.QUOTE;
        }
        if (maxConcurrentPromotions <= 0) {
            maxConcurrentPromotions = topN > 0 ? topN : 10;
        }
    }
}
