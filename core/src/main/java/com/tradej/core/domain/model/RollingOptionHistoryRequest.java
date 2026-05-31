package com.tradej.core.domain.model;

import com.tradej.core.domain.instrument.RollingOptionSeriesKey;

import java.time.LocalDate;

public record RollingOptionHistoryRequest(
        RollingOptionSeriesKey series,
        LocalDate fromDate,
        LocalDate toDate
) {
    public RollingOptionHistoryRequest {
        if (series == null) {
            throw new IllegalArgumentException("series is required");
        }
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate are required");
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate must be on/after fromDate");
        }
    }
}
