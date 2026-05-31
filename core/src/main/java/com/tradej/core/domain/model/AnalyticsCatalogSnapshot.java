package com.tradej.core.domain.model;

import java.time.LocalDate;
import java.util.Map;

public record AnalyticsCatalogSnapshot(
        String equityRoot,
        String optionsWarehousePath,
        long equitySymbolCount,
        long equityBarFileCount,
        LocalDate equityMinMonth,
        LocalDate equityMaxMonth,
        long universeRowCount,
        long optionsBarCount,
        LocalDate optionsMinDate,
        LocalDate optionsMaxDate,
        boolean optionsAttached,
        boolean runtimeAttached,
        Map<String, Object> views
) {
}
