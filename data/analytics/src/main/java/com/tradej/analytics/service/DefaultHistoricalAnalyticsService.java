package com.tradej.analytics.service;

import com.tradej.analytics.catalog.HistoricalDataCatalog;
import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.AnalyticsCatalogSnapshot;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;
import com.tradej.core.domain.model.UniverseEntry;
import com.tradej.core.domain.port.HistoricalAnalyticsService;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;

import java.util.List;

public final class DefaultHistoricalAnalyticsService implements HistoricalAnalyticsService, AutoCloseable {

    private final HistoricalBarRepository equityRepository;
    private final RollingOptionHistoricalRepository optionRepository;
    private final HistoricalDataCatalog catalog;
    private final DuckDbAnalyticsEngine engine;

    public DefaultHistoricalAnalyticsService(
            HistoricalBarRepository equityRepository,
            RollingOptionHistoricalRepository optionRepository,
            HistoricalDataCatalog catalog,
            DuckDbAnalyticsEngine engine
    ) {
        this.equityRepository = equityRepository;
        this.optionRepository = optionRepository;
        this.catalog = catalog;
        this.engine = engine;
    }

    @Override
    public List<Candle> queryEquityCandles(CandleHistoryRequest request) {
        return equityRepository.queryCandles(request);
    }

    @Override
    public List<RollingOptionBar> queryOptionBars(RollingOptionSeriesRequest request) {
        return optionRepository.queryBars(request);
    }

    @Override
    public List<UniverseEntry> queryEquityUniverse() {
        return equityRepository.queryUniverse();
    }

    @Override
    public AnalyticsCatalogSnapshot catalog() {
        return catalog.snapshot();
    }

    @Override
    public AnalyticsQueryResult executeReadOnlySql(String sql, int rowLimit) {
        try {
            return engine.executeReadOnlySql(sql, rowLimit);
        } catch (Exception ex) {
            throw new IllegalStateException("Analytics SQL query failed", ex);
        }
    }

    @Override
    public void close() {
        engine.close();
    }
}
