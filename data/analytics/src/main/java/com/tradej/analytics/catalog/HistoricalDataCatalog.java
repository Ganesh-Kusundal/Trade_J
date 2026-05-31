package com.tradej.analytics.catalog;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsCatalogSnapshot;

import java.sql.SQLException;

public final class HistoricalDataCatalog {

    private final DuckDbAnalyticsEngine engine;

    public HistoricalDataCatalog(DuckDbAnalyticsEngine engine) {
        this.engine = engine;
    }

    public AnalyticsCatalogSnapshot snapshot() {
        try {
            return engine.catalogSnapshot();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to build analytics catalog snapshot", ex);
        }
    }

    public boolean healthy() {
        try {
            AnalyticsCatalogSnapshot snapshot = snapshot();
            return snapshot.equitySymbolCount() > 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
