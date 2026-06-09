package com.tradej.app.health;

import com.tradej.analytics.catalog.HistoricalDataCatalog;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsHealthIndicator implements HealthIndicator {

    private final HistoricalDataCatalog catalog;
    private final AlertManager alertManager;

    public AnalyticsHealthIndicator(HistoricalDataCatalog catalog, AlertManager alertManager) {
        this.catalog = catalog;
        this.alertManager = alertManager;
    }

    @Override
    public Health health() {
        try {
            if (catalog.healthy()) {
                return Health.up().withDetail("catalog", catalog.snapshot()).build();
            }
            if (alertManager != null) {
                alertManager.warning("analytics", "No equity symbols in analytics catalog");
            }
            return Health.down().withDetail("reason", "No equity symbols in analytics catalog").build();
        } catch (RuntimeException ex) {
            if (alertManager != null) {
                alertManager.critical("analytics", "Analytics catalog error: " + ex.getMessage());
            }
            return Health.down(ex).build();
        }
    }
}
