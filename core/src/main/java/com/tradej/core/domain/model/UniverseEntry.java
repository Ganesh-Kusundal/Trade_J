package com.tradej.core.domain.model;

import java.time.LocalDate;

public record UniverseEntry(
        String symbol,
        String companyName,
        String isin,
        String industry,
        String macroSector,
        LocalDate asOfDate
) {
}
