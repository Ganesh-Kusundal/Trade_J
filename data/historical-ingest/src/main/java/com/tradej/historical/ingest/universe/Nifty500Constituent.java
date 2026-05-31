package com.tradej.historical.ingest.universe;

import java.time.LocalDate;

public record Nifty500Constituent(
        String symbol,
        String companyName,
        String industry,
        String macroSector,
        String isin,
        LocalDate asOfDate
) {
}
