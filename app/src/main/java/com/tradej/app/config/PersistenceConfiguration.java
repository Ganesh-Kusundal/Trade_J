package com.tradej.app.config;

import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.execution.identity.OrderIdentityRehydrator;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.chronicle.ChronicleDeadLetterQueue;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.persistence.replay.HistoricalRangeService;
import com.tradej.persistence.replay.ReplayClock;
import com.tradej.persistence.replay.ReplayRunner;
import com.tradej.core.domain.port.EventBus;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.replay.engine.IsolatedReplayStateManager;
import com.tradej.replay.engine.ReplayOrchestrator;
import com.tradej.replay.engine.PositionStateRebuilder;
import com.tradej.execution.readmodel.ReadModelStore;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import com.tradej.persistence.replay.ReplayStateManager;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;

/**
 * Configures persistence services: Chronicle Queue audit log, DuckDB event store,
 * and OMS event-sourced order repository.
 *
 * <p>The {@link EventSourcedOrderRepository} stores all OSM order lifecycle events
 * using Chronicle Queue for append-only persistence and rebuilds state on startup.
 *
 * <p>Separates persistence concerns from general application wiring (Phase A.2).
 */
@Configuration
public class PersistenceConfiguration {

    private static final String OMS_QUEUE_SUBDIR = "oms";
    private static final String DLQ_QUEUE_SUBDIR = "dlq";

    @Bean(destroyMethod = "close")
    DeadLetterQueue deadLetterQueue(TradingProperties properties) {
        Path dlqPath = Path.of(properties.storage().chroniclePath()).resolve(DLQ_QUEUE_SUBDIR);
        return new ChronicleDeadLetterQueue(dlqPath);
    }

    @Bean
    ChronicleAuditLogWriter chronicleAuditLogWriter(TradingProperties properties) {
        return new ChronicleAuditLogWriter(Path.of(properties.storage().chroniclePath()));
    }

    @Bean
    DuckDbEventStore duckDbEventStore(TradingProperties properties) {
        return new DuckDbEventStore(Path.of(properties.storage().duckdbPath()));
    }

    @Bean(destroyMethod = "close")
    AsyncDuckDbEventStore asyncDuckDbEventStore(DuckDbEventStore duckDbEventStore) {
        AsyncDuckDbEventStore store = new AsyncDuckDbEventStore(duckDbEventStore);
        store.start();
        return store;
    }

    @Bean
    EventSourcedOrderRepository eventSourcedOrderRepository(TradingProperties properties) {
        Path chronicleBase = Path.of(properties.storage().chroniclePath());
        Path omsQueuePath = chronicleBase.resolve(OMS_QUEUE_SUBDIR);
        return new EventSourcedOrderRepository(omsQueuePath);
    }

    @Bean(name = "localHistoricalRangeService")
    HistoricalRangeService localHistoricalRangeService(TradingProperties properties) {
        return new HistoricalRangeService(Path.of(properties.storage().duckdbPath()));
    }

    @Bean
    DuckDbPipelineGraphStore duckDbPipelineGraphStore(TradingProperties properties) {
        return new DuckDbPipelineGraphStore(Path.of(properties.storage().duckdbPath()));
    }

    @Bean(destroyMethod = "close")
    ReplayRunner replayRunner(TradingProperties properties, EventBus eventBus, VirtualClock virtualClock, ReplayStateManager replayStateManager) {
        return new ReplayRunner(Path.of(properties.storage().chroniclePath()), eventBus, virtualClock, replayStateManager);
    }

    @Bean
    ReplayStateManager replayStateManager(
            PortfolioEngine portfolioEngine,
            EventSourcedNetPositionProvider netPositionProvider,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            ReadModelStore readModelStore,
            OrderManagementService orderManagementService
    ) {
        return new IsolatedReplayStateManager(
                portfolioEngine,
                netPositionProvider,
                positionRiskHandler,
                candleAggregationService,
                readModelStore,
                orderManagementService
        );
    }

    @Bean(destroyMethod = "close")
    @Profile("replay")
    ReplayClock replayClock(EventBus eventBus) {
        return new ReplayClock(eventBus);
    }

    @Bean
    ApplicationRunner orderIdentityRehydrationRunner(
            EventSourcedOrderRepository omsRepo,
            OrderIdentityRegistry identityRegistry
    ) {
        return args -> OrderIdentityRehydrator.rehydrate(omsRepo, identityRegistry);
    }

    @Bean
    ReplayOrchestrator replayOrchestrator(
            ReplayRunner replayRunner,
            HistoricalRangeService localHistoricalRangeService,
            VirtualClock virtualClock,
            ReplayStateManager replayStateManager
    ) {
        return new ReplayOrchestrator(replayRunner, localHistoricalRangeService, virtualClock, replayStateManager);
    }

    @Bean
    PositionStateRebuilder positionStateRebuilder(ReplayOrchestrator replayOrchestrator) {
        return new PositionStateRebuilder(replayOrchestrator);
    }
}
