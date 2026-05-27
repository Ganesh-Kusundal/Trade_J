package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;

public record RollingOptionHistoryRequest(
        String underlying,
        ExchangeSegment exchangeSegment,
        int intervalMinutes,
        String expiryFlag,
        int expiryCode,
        String strike,
        String optionType,
        LocalDate fromDate,
        LocalDate toDate
) {
}
