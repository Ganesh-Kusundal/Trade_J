package com.tradej.core.domain.model;

import com.tradej.core.domain.value.OptionType;

public record RollingOptionSeriesRequest(
        String underlying,
        String expiryKind,
        int expiryCode,
        int strikeOffset,
        OptionType optionType,
        int intervalMin,
        long fromMs,
        long toMs,
        int limit
) {
    public RollingOptionSeriesRequest {
        if (limit <= 0) {
            limit = 10_000;
        }
    }
}
