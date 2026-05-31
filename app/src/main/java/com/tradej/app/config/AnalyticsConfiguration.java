package com.tradej.app.config;

import com.tradej.analytics.catalog.HistoricalDataCatalog;
import com.tradej.analytics.config.DuckDbAnalyticsConfig;
import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.analytics.repository.DuckDbRollingOptionHistoricalRepository;
import com.tradej.analytics.repository.FederatedHistoricalBarRepository;
import com.tradej.analytics.service.DefaultHistoricalAnalyticsService;
import com.tradej.core.domain.port.HistoricalAnalyticsService;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;
import com.tradej.core.infrastructure.WorkspacePaths;
import com.tradej.analytics.pipeline.AnalyticsPipelineSharedState;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Map;

@Configuration
public class AnalyticsConfiguration {

    @Bean(destroyMethod = "close")
    DuckDbAnalyticsEngine duckDbAnalyticsEngine(TradingProperties properties, WorkspacePaths workspacePaths) {
        TradingProperties.AnalyticsProperties analytics = properties.analytics();
        Path equityRoot = workspacePaths.historicalEquityRoot(analytics.equityRoot());
        Path optionsWarehouse = Path.of(analytics.optionsWarehouse());
        if (!optionsWarehouse.isAbsolute()) {
            optionsWarehouse = workspacePaths.resolve(analytics.optionsWarehouse());
        }
        Path runtimeDb = analytics.runtimeDbPath() == null || analytics.runtimeDbPath().isBlank()
                ? workspacePaths.resolve("runtime-dev/trade.duckdb")
                : Path.of(analytics.runtimeDbPath());
        if (!runtimeDb.isAbsolute()) {
            runtimeDb = workspacePaths.resolve(analytics.runtimeDbPath());
        }
        return new DuckDbAnalyticsEngine(new DuckDbAnalyticsConfig(
                equityRoot,
                optionsWarehouse,
                runtimeDb,
                analytics.attachRuntimeDb(),
                analytics.sqlEnabled(),
                analytics.sqlMaxRows(),
                analytics.sqlMaxRuntimeMs()
        ));
    }

    @Bean
    HistoricalDataCatalog historicalDataCatalog(DuckDbAnalyticsEngine engine) {
        return new HistoricalDataCatalog(engine);
    }

    @Bean
    HistoricalBarRepository historicalBarRepository(DuckDbAnalyticsEngine engine) {
        return new FederatedHistoricalBarRepository(engine);
    }

    @Bean
    RollingOptionHistoricalRepository rollingOptionHistoricalRepository(DuckDbAnalyticsEngine engine) {
        return new DuckDbRollingOptionHistoricalRepository(engine);
    }

    @Bean
    HistoricalAnalyticsService historicalAnalyticsService(
            HistoricalBarRepository historicalBarRepository,
            RollingOptionHistoricalRepository rollingOptionHistoricalRepository,
            HistoricalDataCatalog historicalDataCatalog,
            DuckDbAnalyticsEngine duckDbAnalyticsEngine
    ) {
        return new DefaultHistoricalAnalyticsService(
                historicalBarRepository,
                rollingOptionHistoricalRepository,
                historicalDataCatalog,
                duckDbAnalyticsEngine
        );
    }

    @Bean
    Map<String, Object> analyticsPipelineSharedState(
            HistoricalBarRepository historicalBarRepository,
            RollingOptionHistoricalRepository rollingOptionHistoricalRepository
    ) {
        return AnalyticsPipelineSharedState.create(
                historicalBarRepository,
                rollingOptionHistoricalRepository
        );
    }
}
