package com.tradej.historical.ingest.store;

import com.tradej.historical.ingest.model.DownloadTaskRecord;
import com.tradej.historical.ingest.model.DownloadTaskStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DuckDbHistoricalWarehouseTaskClaimTest {

    @TempDir
    Path tempDir;

    @Test
    void claimNextTaskIsExclusiveUnderConcurrentWorkers() throws Exception {
        Path dbPath = tempDir.resolve("claim-test.duckdb");
        String jobId = "job-1";
        List<DownloadTaskRecord> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(task(jobId, "NIFTY", i));
        }

        try (DuckDbHistoricalWarehouse warehouse = new DuckDbHistoricalWarehouse(dbPath)) {
            warehouse.insertTasks(tasks);

            Set<String> claimed = ConcurrentHashMap.newKeySet();
            ExecutorService executor = Executors.newFixedThreadPool(4);
            for (int w = 0; w < 4; w++) {
                executor.submit(() -> {
                    try {
                        while (true) {
                            Optional<DownloadTaskRecord> claim = warehouse.claimNextTask(jobId);
                            if (claim.isEmpty()) {
                                break;
                            }
                            String taskId = claim.get().taskId();
                            assertTrue(claimed.add(taskId), "duplicate claim for " + taskId);
                        }
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            assertEquals(20, claimed.size());
            assertEquals(0, warehouse.listPendingTasks(jobId).size());
        }
    }

    @Test
    void resetStaleRunningTasksReturnsTasksToPending() throws Exception {
        Path dbPath = tempDir.resolve("reset-test.duckdb");
        String jobId = "job-2";
        DownloadTaskRecord pending = task(jobId, "BANKNIFTY", 0);

        try (DuckDbHistoricalWarehouse warehouse = new DuckDbHistoricalWarehouse(dbPath)) {
            warehouse.insertTasks(List.of(pending));
            Optional<DownloadTaskRecord> claimed = warehouse.claimNextTask(jobId);
            assertTrue(claimed.isPresent());
            assertEquals(DownloadTaskStatus.RUNNING, claimed.get().status());

            int reset = warehouse.resetStaleRunningTasks(jobId);
            assertEquals(1, reset);
            assertEquals(1, warehouse.listPendingTasks(jobId).size());
        }
    }

    private static DownloadTaskRecord task(String jobId, String underlying, int index) {
        return new DownloadTaskRecord(
                UUID.randomUUID().toString(),
                jobId,
                underlying + "-fp-" + index,
                underlying,
                "WEEK",
                1,
                0,
                "CALL",
                5,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 30),
                DownloadTaskStatus.PENDING,
                0L,
                null,
                null
        );
    }
}
