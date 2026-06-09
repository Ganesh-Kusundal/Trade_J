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
            WorkspacePaths workspacePaths,
            com.tradej.historical.ingest.canonical.ParquetWriteService parquetWriteService
    ) {
        TradingProperties.HistoricalEquityProperties equity = properties.historicalEquity();
        return new EquityDownloadJobService(
                workspacePaths.historicalEquityRoot(equity.rootPath()),
                marketDataProvider,
                instrumentResolver,
                equity.workers(),
                System::currentTimeMillis,
                () -> sleep(equity.delayMs()),
                equity.universeUrl(),
                parquetWriteService
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

    @Bean
    com.tradej.historical.ingest.calendar.TradingCalendarStore tradingCalendarStore() {
        return new com.tradej.historical.ingest.calendar.TradingCalendarStore();
    }

    @Bean
    com.tradej.historical.ingest.calendar.CompositeHolidayCalendar compositeHolidayCalendar(
            com.tradej.historical.ingest.calendar.TradingCalendarStore tradingCalendarStore,
            @org.springframework.beans.factory.annotation.Qualifier("canonicalDataRoot") Path dataRoot) {
        var calendar = new com.tradej.historical.ingest.calendar.CompositeHolidayCalendar(tradingCalendarStore);
        calendar.refreshFromData(dataRoot);
        return calendar;
    }

    @Bean
    com.tradej.historical.ingest.sync.DataGapScanService dataGapScanService(
            com.tradej.historical.ingest.calendar.CompositeHolidayCalendar calendar,
            @org.springframework.beans.factory.annotation.Qualifier("canonicalDataRoot") Path dataRoot) {
        return new com.tradej.historical.ingest.sync.DataGapScanService(calendar, dataRoot);
    }

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("canonicalDataRoot")
    Path canonicalDataRoot(TradingProperties properties, WorkspacePaths workspacePaths) {
        return workspacePaths.historicalEquityRoot(
                properties.historicalEquity().rootPath()).getParent();
    }

    @Bean
    com.tradej.historical.ingest.sync.GapDetector gapDetector(
            com.tradej.historical.ingest.calendar.TradingCalendarStore calendar) {
        return new com.tradej.historical.ingest.sync.GapDetector(calendar);
    }

    @Bean
    com.tradej.historical.ingest.canonical.ParquetWriteService parquetWriteService(
            @org.springframework.beans.factory.annotation.Qualifier("canonicalDataRoot") Path dataRoot) {
        return new com.tradej.historical.ingest.canonical.CanonicalBarWriter(dataRoot);
    }

    @Bean
    com.tradej.historical.ingest.canonical.CanonicalBarQuery canonicalBarQuery(
            @org.springframework.beans.factory.annotation.Qualifier("canonicalDataRoot") Path dataRoot) {
        return new com.tradej.historical.ingest.canonical.CanonicalBarQuery(
                com.tradej.historical.ingest.canonical.CanonicalPaths.barsRoot(dataRoot));
    }

    @Bean
    com.tradej.historical.ingest.canonical.MultiIntervalGenerator multiIntervalGenerator(
            com.tradej.historical.ingest.canonical.HistoricalDataStore dataStore,
            com.tradej.historical.ingest.canonical.ParquetWriteService writer) {
        return new com.tradej.historical.ingest.canonical.MultiIntervalGenerator(
                (com.tradej.historical.ingest.canonical.ParquetHistoricalDataStore) dataStore, writer);
    }

    @Bean
    com.tradej.historical.ingest.canonical.HistoricalDataStore historicalDataStore(
            TradingProperties properties,
            WorkspacePaths workspacePaths,
            com.tradej.historical.ingest.calendar.TradingCalendarStore calendar) {
        Path dataRoot = workspacePaths.historicalEquityRoot(
                properties.historicalEquity().rootPath()).getParent();
        return new com.tradej.historical.ingest.canonical.ParquetHistoricalDataStore(
                dataRoot, calendar);
    }

    @Bean
    com.tradej.historical.ingest.replay.ParquetReplayAdapter parquetReplayAdapter(
            com.tradej.historical.ingest.canonical.HistoricalDataStore dataStore) {
        return new com.tradej.historical.ingest.replay.ParquetReplayAdapter(dataStore);
    }

    @Bean
    @ConditionalOnBean(MarketDataProvider.class)
    com.tradej.historical.ingest.sync.IncrementalSyncService incrementalSyncService(
            MarketDataProvider marketDataProvider,
            InstrumentResolver instrumentResolver,
            com.tradej.historical.ingest.calendar.TradingCalendarStore calendar,
            com.tradej.historical.ingest.canonical.ParquetWriteService parquetWriteService) {
        return new com.tradej.historical.ingest.sync.IncrementalSyncService(
                marketDataProvider, instrumentResolver, calendar, parquetWriteService);
    }

    @Bean
    @ConditionalOnBean(MarketDataProvider.class)
    com.tradej.historical.ingest.sync.BackfillService backfillService(
            com.tradej.historical.ingest.sync.GapDetector gapDetector,
            MarketDataProvider marketDataProvider,
            com.tradej.historical.ingest.canonical.ParquetWriteService parquetWriteService) {
        return new com.tradej.historical.ingest.sync.BackfillService(
                gapDetector, marketDataProvider, parquetWriteService);
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
