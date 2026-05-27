package com.tradej.core.domain.model;

import java.time.LocalDate;

public record CandleHistoryRequest(
        InstrumentKey instrument,
        String interval,
        LocalDate fromDate,
        LocalDate toDate
) {
}
