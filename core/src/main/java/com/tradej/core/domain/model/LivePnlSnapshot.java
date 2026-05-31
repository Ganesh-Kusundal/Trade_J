package com.tradej.core.domain.model;

public record LivePnlSnapshot(
        long netUnrealizedPnlPaisa,
        long netQuantity
) {
}
