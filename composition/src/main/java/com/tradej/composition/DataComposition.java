package com.tradej.composition;

import com.tradej.composition.config.StorageProfile;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.chronicle.ChronicleDeadLetterQueue;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import com.tradej.persistence.duckdb.DuckDbConnectionPool;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import com.tradej.persistence.duckdb.DuckDbScanStore;
import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Data composition root — owns the canonical persistence layer
 * (Chronicle queues for audit + DLQ, DuckDB for events, scans, and pipeline graphs).
 *
 * <p>Mirrors the bean wiring that {@code app/.../config/DataConfiguration.java:120-165}
 * historically performed.
 */
public final class DataComposition implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DataComposition.class);

    private static final String DLQ_QUEUE_SUBDIR = "dlq";

    private final StorageProfile profile;
    private final DeadLetterQueue deadLetterQueue;
    private final ChronicleAuditLogWriter chronicleAuditLogWriter;
    private final DuckDbConnectionPool duckDbConnectionPool;
    private final DuckDbEventStore duckDbEventStore;
    private final AsyncDuckDbEventStore asyncDuckDbEventStore;
    private final DuckDbPipelineGraphStore duckDbPipelineGraphStore;
    private final DuckDbScanStore duckDbScanStore;

    private DataComposition(
            StorageProfile profile,
            DeadLetterQueue deadLetterQueue,
            ChronicleAuditLogWriter chronicleAuditLogWriter,
            DuckDbConnectionPool duckDbConnectionPool,
            DuckDbEventStore duckDbEventStore,
            AsyncDuckDbEventStore asyncDuckDbEventStore,
            DuckDbPipelineGraphStore duckDbPipelineGraphStore,
            DuckDbScanStore duckDbScanStore
    ) {
        this.profile = profile;
        this.deadLetterQueue = deadLetterQueue;
        this.chronicleAuditLogWriter = chronicleAuditLogWriter;
        this.duckDbConnectionPool = duckDbConnectionPool;
        this.duckDbEventStore = duckDbEventStore;
        this.asyncDuckDbEventStore = asyncDuckDbEventStore;
        this.duckDbPipelineGraphStore = duckDbPipelineGraphStore;
        this.duckDbScanStore = duckDbScanStore;
    }

    /**
     * Build the data composition from a {@link StorageProfile}.
     *
     * @param profile storage profile (chronicle path + duckdb path)
     * @return fully-wired {@link DataComposition}
     * @throws NullPointerException if {@code profile} is null
     */
    public static DataComposition create(StorageProfile profile) {
        Objects.requireNonNull(profile, "profile");

        Path dlqPath = profile.chroniclePath().resolve(DLQ_QUEUE_SUBDIR);
        ChronicleDeadLetterQueue dlq = new ChronicleDeadLetterQueue(dlqPath);
        ChronicleAuditLogWriter audit = new ChronicleAuditLogWriter(profile.chroniclePath());

        DuckDbConnectionPool pool = DuckDbConnectionPool.create(profile.duckdbPath());
        DuckDbEventStore eventStore = new DuckDbEventStore(pool);
        AsyncDuckDbEventStore asyncEventStore = new AsyncDuckDbEventStore(eventStore);
        asyncEventStore.start();

        DuckDbPipelineGraphStore pipelineGraphStore = new DuckDbPipelineGraphStore(pool);
        DuckDbScanStore scanStore = new DuckDbScanStore(profile.duckdbPath());

        log.info("DataComposition created (chronicle={}, duckdb={})",
                profile.chroniclePath(), profile.duckdbPath());

        return new DataComposition(profile, dlq, audit, pool, eventStore, asyncEventStore, pipelineGraphStore, scanStore);
    }

    public StorageProfile profile() {
        return profile;
    }

    public DeadLetterQueue deadLetterQueue() {
        return deadLetterQueue;
    }

    public ChronicleAuditLogWriter chronicleAuditLogWriter() {
        return chronicleAuditLogWriter;
    }

    public DuckDbConnectionPool duckDbConnectionPool() {
        return duckDbConnectionPool;
    }

    public DuckDbEventStore duckDbEventStore() {
        return duckDbEventStore;
    }

    public AsyncDuckDbEventStore asyncDuckDbEventStore() {
        return asyncDuckDbEventStore;
    }

    public DuckDbPipelineGraphStore duckDbPipelineGraphStore() {
        return duckDbPipelineGraphStore;
    }

    public DuckDbScanStore duckDbScanStore() {
        return duckDbScanStore;
    }

    @Override
    public void close() throws Exception {
        try {
            asyncDuckDbEventStore.close();
        } catch (Exception e) {
            log.warn("Failed to close asyncDuckDbEventStore: {}", e.getMessage());
        }
        try {
            duckDbScanStore.close();
        } catch (Exception e) {
            log.warn("Failed to close duckDbScanStore: {}", e.getMessage());
        }
        try {
            duckDbConnectionPool.close();
        } catch (Exception e) {
            log.warn("Failed to close duckDbConnectionPool: {}", e.getMessage());
        }
    }
}
