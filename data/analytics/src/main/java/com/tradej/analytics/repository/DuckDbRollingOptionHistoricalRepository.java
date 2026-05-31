package com.tradej.analytics.repository;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class DuckDbRollingOptionHistoricalRepository implements RollingOptionHistoricalRepository {

    private final DuckDbAnalyticsEngine engine;

    public DuckDbRollingOptionHistoricalRepository(DuckDbAnalyticsEngine engine) {
        this.engine = engine;
    }

    @Override
    public List<RollingOptionBar> queryBars(RollingOptionSeriesRequest request) {
        try {
            return engine.queryRollingOptionBars(
                    request.underlying(),
                    request.expiryKind(),
                    request.expiryCode(),
                    request.strikeOffset(),
                    request.optionType().name(),
                    request.intervalMin(),
                    request.fromMs(),
                    request.toMs(),
                    request.limit()
            );
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query rolling option bars for " + request.underlying(), ex);
        }
    }

    @Override
    public Optional<LocalDate> latestAvailableTradingDay(String underlying, int lookbackDays) {
        try {
            return engine.latestOptionTradingDay(underlying, lookbackDays);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to resolve latest option trading day for " + underlying, ex);
        }
    }

    @Override
    public List<String> availableUnderlyings() {
        try {
            return engine.availableOptionUnderlyings();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list option underlyings", ex);
        }
    }
}
