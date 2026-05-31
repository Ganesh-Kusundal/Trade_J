package com.tradej.app.config;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.historical.ingest.importing.HiveCacheEquityImporter;
import com.tradej.core.infrastructure.WorkspacePaths;
import com.tradej.historical.ingest.service.DownloadJobRegistry;
import com.tradej.historical.ingest.service.DownloadJobService;
import com.tradej.historical.ingest.service.EquityDownloadJobService;
import com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class HistoricalDownloadConfiguration {

    @Bean(destroyMethod = "close")
    DuckDbHistoricalWarehouse duckDbHistoricalWarehouse(TradingProperties properties) {
        return new DuckDbHistoricalWarehouse(Path.of(properties.storage().historicalWarehousePath()));
    }

    @Bean
    DownloadJobService downloadJobService(
            DuckDbHistoricalWarehouse warehouse,
            OptionsProvider optionsProvider,
            TradingProperties properties
    ) {
        long delayMs = properties.download().delayMs();
        int workers = properties.download().workers();
        return new DownloadJobService(
                warehouse,
                optionsProvider,
                workers,
                System::currentTimeMillis,
                () -> sleep(delayMs)
        );
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnBean(MarketDataProvider.class)
    EquityDownloadJobService equityDownloadJobService(
            MarketDataProvider marketDataProvider,
            InstrumentResolver instrumentResolver,
            TradingProperties properties,
            WorkspacePaths workspacePaths
    ) {
        TradingProperties.HistoricalEquityProperties equity = properties.historicalEquity();
        return new EquityDownloadJobService(
                workspacePaths.historicalEquityRoot(equity.rootPath()),
                marketDataProvider,
                instrumentResolver,
                equity.workers(),
                System::currentTimeMillis,
                () -> sleep(equity.delayMs()),
                equity.universeUrl()
        );
    }

    @Bean
    HiveCacheEquityImporter hiveCacheEquityImporter() {
        return new HiveCacheEquityImporter();
    }

    @Bean(destroyMethod = "close")
    DownloadJobRegistry downloadJobRegistry(TradingProperties properties, WorkspacePaths workspacePaths) {
        Path optionsWarehouse = workspacePaths.resolve(properties.storage().historicalWarehousePath());
        Path equityRoot = workspacePaths.historicalEquityRoot(properties.historicalEquity().rootPath());
        return new DownloadJobRegistry(optionsWarehouse, equityRoot);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService historicalDownloadExecutor(TradingProperties properties) {
        int workers = Math.max(1, properties.historicalEquity().workers());
        return Executors.newFixedThreadPool(workers, r -> {
            Thread thread = new Thread(r, "historical-download");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
