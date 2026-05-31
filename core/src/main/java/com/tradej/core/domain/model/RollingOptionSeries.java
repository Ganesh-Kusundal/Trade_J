package com.tradej.core.domain.model;

import java.util.List;

/**
 * Typed response from Dhan {@code /charts/rollingoption} for one request window.
 */
public record RollingOptionSeries(
        RollingOptionHistoryRequest request,
        List<RollingOptionBar> bars
) {
    public RollingOptionSeries {
        bars = bars == null ? List.of() : List.copyOf(bars);
    }
}
