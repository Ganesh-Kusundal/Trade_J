package com.tradej.composition;

import com.tradej.composition.config.StorageProfile;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.chronicle.ChronicleDeadLetterQueue;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import com.tradej.persistence.duckdb.DuckDbConnectionPool;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import com.tradej.persistence.duckdb.DuckDbScanStore;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import com.tradej.persistence.replay.HistoricalRangeService;
import com.tradej.persistence.replay.ReplayRunner;
import com.tradej.core.domain.port.DeadLetterQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class DataComposition {

    private static final Logger log = LoggerFactory.getLogger(DataComposition.class);
    private static final String OMS_QUEUE_SUBDIR = "oms";
    private static final String DLQ_QUEUE_SUBDIR = "dlq";

    private final StorageProfile storageProfile;
    private final DeadLetterQueue deadLetterQueue;
    private final ChronicleAuditLogWriter chronicleAuditLogWriter;
    private final DuckDbConnectionPool duckDbPool;
    private final DuckDbEventStore duckDbEventStore;
    private final AsyncDuckDbEventStore asyncDuckDbEventStore;
    private final DuckDbPipelineGraphStore pipelineGraphStore;
    private final DuckDbScanStore scanStore;

    private DataComposition(
            StorageProfile storageProfile,
            DeadLetterQueue deadLetterQueue,
            ChronicleAuditLogWriter chronicleAuditLogWriter,
            DuckDbConnectionPool duckDbPool,
            DuckDbEventStore duckDbEventStore,
            AsyncDuckDbEventStore asyncDuckDbEventStore,
            DuckDbPipelineGraphStore pipelineGraphStore,
            DuckDbScanStore scanStore
    ) {
        this.storageProfile = storageProfile;
        this.deadLetterQueue = deadLetterQueue;
        this.chronicleAuditLogWriter = chronicleAuditLogWriter;
        this.duckDbPool = duckDbPool;
        this.duckDbEventStore = duckDbEventStore;
        this.asyncDuckDbEventStore = asyncDuckDbEventStore;
        this.pipelineGraphStore = pipelineGraphStore;
        this.scanStore = scanStore;
    }

    public static DataComposition create(StorageProfile storageProfile) {
        Path chroniclePath = storageProfile.chroniclePath();
        Path duckdbPath = storageProfile.duckdbPath();

        DeadLetterQueue dlq = new ChronicleDeadLetterQueue(chroniclePath.resolve(DLQ_QUEUE_SUBDIR));
        ChronicleAuditLogWriter auditLog = new ChronicleAuditLogWriter(chroniclePath);

        DuckDbConnectionPool pool = DuckDbConnectionPool.create(duckdbPath);
        DuckDbEventStore eventStore = new DuckDbEventStore(pool);
        AsyncDuckDbEventStore asyncEventStore = new AsyncDuckDbEventStore(eventStore);
        DuckDbPipelineGraphStore graphStore = new DuckDbPipelineGraphStore(pool);
        DuckDbScanStore scanStore = new DuckDbScanStore(pool);

        log.info("Data composition created: chronicle={} duckdb={} (shared pool)", chroniclePath, duckdbPath);
        return new DataComposition(storageProfile, dlq, auditLog, pool, eventStore, asyncEventStore, graphStore, scanStore);
    }

    public StorageProfile storageProfile() {
        return storageProfile;
    }

    public DeadLetterQueue deadLetterQueue() {
        return deadLetterQueue;
    }

    public ChronicleAuditLogWriter chronicleAuditLogWriter() {
        return chronicleAuditLogWriter;
    }

    public DuckDbEventStore duckDbEventStore() {
        return duckDbEventStore;
    }

    public AsyncDuckDbEventStore asyncDuckDbEventStore() {
        return asyncDuckDbEventStore;
    }

    public DuckDbPipelineGraphStore pipelineGraphStore() {
        return pipelineGraphStore;
    }

    public DuckDbConnectionPool duckDbPool() {
        return duckDbPool;
    }

    public DuckDbScanStore scanStore() {
        return scanStore;
    }
}
