package com.tradej.core.domain.model;

public record MarginEstimate(
        long totalMarginPaisa,
        long spanMarginPaisa,
        long exposureMarginPaisa,
        long brokeragePaisa
) {
}
