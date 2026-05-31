package com.tradej.app.health;

import com.tradej.analytics.catalog.HistoricalDataCatalog;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsHealthIndicator implements HealthIndicator {

    private final HistoricalDataCatalog catalog;

    public AnalyticsHealthIndicator(HistoricalDataCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Health health() {
        try {
            if (catalog.healthy()) {
                return Health.up().withDetail("catalog", catalog.snapshot()).build();
            }
            return Health.down().withDetail("reason", "No equity symbols in analytics catalog").build();
        } catch (RuntimeException ex) {
            return Health.down(ex).build();
        }
    }
}
