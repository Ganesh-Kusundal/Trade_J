package com.tradej.historical.ingest.model;

import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

import java.time.LocalDate;
import java.util.List;

public record RollingOptionDownloadConfig(
        List<String> symbols,
        ExchangeSegment exchangeSegment,
        LocalDate fromDate,
        LocalDate toDate,
        List<Integer> intervalMinutes,
        List<RollingExpiryRoll> expiries,
        List<StrikeOffset> strikes,
        List<OptionType> optionTypes,
        long delayMs,
        boolean resumeExistingJob
) {
    public RollingOptionDownloadConfig {
        symbols = symbols.stream().map(ContractSymbolNormalizer::normalize).toList();
        intervalMinutes = List.copyOf(intervalMinutes);
        expiries = List.copyOf(expiries);
        strikes = List.copyOf(strikes);
        optionTypes = List.copyOf(optionTypes);
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("At least one symbol is required");
        }
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate are required");
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate must be on/after fromDate");
        }
    }
}
