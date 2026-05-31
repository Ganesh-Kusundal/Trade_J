package com.tradej.historical.ingest.store;

import com.tradej.core.domain.instrument.RollingExpiryKind;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.historical.ingest.model.DownloadJobRecord;
import com.tradej.historical.ingest.model.DownloadJobStatus;
import com.tradej.historical.ingest.model.DownloadSourceType;
import com.tradej.historical.ingest.model.DownloadTaskRecord;
import com.tradej.historical.ingest.model.DownloadTaskStatus;
import com.tradej.historical.ingest.model.RollingOptionDownloadConfig;
import com.tradej.historical.ingest.planner.RollingOptionDownloadPlanner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("component")
class DuckDbHistoricalWarehouseTest {

    @TempDir
    Path tempDir;

    @Test
    void upsertsBarsAndTracksDownloadTasksWithCanonicalKeys() throws Exception {
        Path dbPath = tempDir.resolve("historical.duckdb");
        try (DuckDbHistoricalWarehouse warehouse = new DuckDbHistoricalWarehouse(dbPath)) {
            long rows = warehouse.upsertRollingOptionBars(
                    "NIFTY",
                    "MONTH",
                    1,
                    0,
                    "CALL",
                    5,
                    List.of(new RollingOptionBar(
                            1_704_067_200_000L,
                            10_000L, 10_100L, 9_900L, 10_050L,
                            1000L, 18.5, 50_000L, 2_200_000L, 2_200_000L
                    ))
            );
            assertEquals(1L, rows);

            List<RollingOptionBar> queried = warehouse.queryRollingOptionBars(
                    "NIFTY", "MONTH", 1, 0, "CALL", 5,
                    1_704_067_000_000L, 1_704_068_000_000L, 10
            );
            assertEquals(1, queried.size());
            assertEquals(10_050L, queried.getFirst().closePaisa());

            String jobId = "job-1";
            warehouse.insertJob(new DownloadJobRecord(
                    jobId,
                    DownloadSourceType.ROLLING_OPTION,
                    DownloadJobStatus.PENDING,
                    "{}",
                    System.currentTimeMillis(),
                    null,
                    null,
                    null
            ));
            RollingOptionDownloadConfig config = new RollingOptionDownloadConfig(
                    List.of("NIFTY"),
                    ExchangeSegment.IDX_I,
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2024, 1, 31),
                    List.of(5),
                    List.of(new RollingExpiryRoll(RollingExpiryKind.MONTH, 1)),
                    StrikeOffset.atmPlusMinus(0),
                    List.of(OptionType.CALL),
                    350L,
                    true
            );
            List<DownloadTaskRecord> tasks = new RollingOptionDownloadPlanner().planTasks(jobId, config);
            warehouse.insertTasks(tasks);
            assertFalse(tasks.isEmpty());
            assertEquals(tasks.size(), warehouse.jobStats(jobId).totalTasks());
            assertEquals(tasks.size(), warehouse.listPendingTasks(jobId).size());
            assertEquals(0, tasks.getFirst().strikeOffset());

            DownloadTaskRecord task = tasks.getFirst();
            warehouse.updateTask(task.taskId(), DownloadTaskStatus.COMPLETED, 10L, null, System.currentTimeMillis());
            assertEquals(1L, warehouse.jobStats(jobId).completedTasks());
        }
    }
}
