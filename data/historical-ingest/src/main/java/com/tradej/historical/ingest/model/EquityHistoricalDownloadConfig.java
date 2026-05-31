package com.tradej.historical.ingest.model;

import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.List;

public record EquityHistoricalDownloadConfig(
        List<String> symbols,
        ExchangeSegment exchangeSegment,
        LocalDate fromDate,
        LocalDate toDate,
        String interval,
        String intervalFolder,
        int intervalMinutes,
        String rootPath,
        long delayMs,
        int workers,
        boolean refreshUniverseOnStart,
        boolean resume
) {
    public EquityHistoricalDownloadConfig {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols must not be empty");
        }
        if (exchangeSegment == null) {
            exchangeSegment = ExchangeSegment.NSE_EQ;
        }
        if (interval == null || interval.isBlank()) {
            interval = "1m";
        }
        if (intervalFolder == null || intervalFolder.isBlank()) {
            intervalFolder = "interval=1m";
        }
        if (intervalMinutes <= 0) {
            intervalMinutes = 1;
        }
        if (rootPath == null || rootPath.isBlank()) {
            rootPath = "data/historical-equity";
        }
    }
}
