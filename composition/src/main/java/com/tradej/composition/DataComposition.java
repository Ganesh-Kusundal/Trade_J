package com.tradej.composition;

import com.tradej.composition.config.StorageProfile;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.chronicle.ChronicleDeadLetterQueue;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import com.tradej.persistence.duckdb.DuckDbEventStore;
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
    private final DuckDbEventStore duckDbEventStore;
    private final AsyncDuckDbEventStore asyncDuckDbEventStore;
    private final DuckDbPipelineGraphStore pipelineGraphStore;

    private DataComposition(
            StorageProfile storageProfile,
            DeadLetterQueue deadLetterQueue,
            ChronicleAuditLogWriter chronicleAuditLogWriter,
            DuckDbEventStore duckDbEventStore,
            AsyncDuckDbEventStore asyncDuckDbEventStore,
            DuckDbPipelineGraphStore pipelineGraphStore
    ) {
        this.storageProfile = storageProfile;
        this.deadLetterQueue = deadLetterQueue;
        this.chronicleAuditLogWriter = chronicleAuditLogWriter;
        this.duckDbEventStore = duckDbEventStore;
        this.asyncDuckDbEventStore = asyncDuckDbEventStore;
        this.pipelineGraphStore = pipelineGraphStore;
    }

    public static DataComposition create(StorageProfile storageProfile) {
        Path chroniclePath = storageProfile.chroniclePath();
        Path duckdbPath = storageProfile.duckdbPath();

        DeadLetterQueue dlq = new ChronicleDeadLetterQueue(chroniclePath.resolve(DLQ_QUEUE_SUBDIR));
        ChronicleAuditLogWriter auditLog = new ChronicleAuditLogWriter(chroniclePath);
        DuckDbEventStore eventStore = new DuckDbEventStore(duckdbPath);
        AsyncDuckDbEventStore asyncEventStore = new AsyncDuckDbEventStore(eventStore);
        DuckDbPipelineGraphStore graphStore = new DuckDbPipelineGraphStore(duckdbPath);

        log.info("Data composition created: chronicle={} duckdb={}", chroniclePath, duckdbPath);
        return new DataComposition(storageProfile, dlq, auditLog, eventStore, asyncEventStore, graphStore);
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
}
