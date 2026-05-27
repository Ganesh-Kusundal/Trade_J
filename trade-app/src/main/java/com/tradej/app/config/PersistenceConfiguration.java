package com.tradej.app.config;

import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Configures persistence services: Chronicle Queue audit log, DuckDB event store,
 * and OMS event-sourced order repository.
 *
 * <p>The {@link EventSourcedOrderRepository} stores all OSM order lifecycle events
 * using Chronicle Queue for append-only persistence and rebuilds state on startup.
 *
 * <p>Extracted from {@link TradingRuntimeConfiguration} to separate persistence
 * concerns from general application wiring (Phase A.2).
 */
@Configuration
public class PersistenceConfiguration {

    private static final String OMS_QUEUE_SUBDIR = "oms";

    @Bean
    ChronicleAuditLogWriter chronicleAuditLogWriter(TradingProperties properties) {
        return new ChronicleAuditLogWriter(Path.of(properties.storage().chroniclePath()));
    }

    @Bean
    DuckDbEventStore duckDbEventStore(TradingProperties properties) {
        return new DuckDbEventStore(Path.of(properties.storage().duckdbPath()));
    }

    @Bean
    EventSourcedOrderRepository eventSourcedOrderRepository(TradingProperties properties) {
        Path chronicleBase = Path.of(properties.storage().chroniclePath());
        Path omsQueuePath = chronicleBase.resolve(OMS_QUEUE_SUBDIR);
        return new EventSourcedOrderRepository(omsQueuePath);
    }
}
