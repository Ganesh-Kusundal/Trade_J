package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.instrument.RollingExpiryKind;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.historical.ingest.model.DownloadJobStats;
import com.tradej.historical.ingest.model.RollingOptionDownloadConfig;
import com.tradej.historical.ingest.planner.RollingOptionDownloadPlanner;
import com.tradej.historical.ingest.service.DownloadJobService;
import com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanRollingOptionDownloadIntegrationTest {

    @TempDir
    Path tempDir;

    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void downloadsAndPersistsRollingOptionBars() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_ROLLING_OPTION_TEST_ENABLED", "dhan.rollingOptionTestEnabled", "false")));

        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-download-cache"), false);

        Path warehousePath = tempDir.resolve("historical.duckdb");
        RollingOptionDownloadConfig config = new RollingOptionDownloadConfig(
                List.of("NIFTY"),
                ExchangeSegment.IDX_I,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 7),
                List.of(5),
                List.of(new RollingExpiryRoll(RollingExpiryKind.MONTH, 1)),
                StrikeOffset.atmPlusMinus(0),
                List.of(OptionType.CALL),
                350L,
                true
        );
        long expectedTasks = RollingOptionDownloadPlanner.estimatedTaskCount(config);
        assertEquals(1L, expectedTasks);

        try (DuckDbHistoricalWarehouse warehouse = new DuckDbHistoricalWarehouse(warehousePath)) {
            DownloadJobService service = new DownloadJobService(
                    warehouse,
                    brokerConnection.options(),
                    2,
                    System::currentTimeMillis,
                    () -> sleep(0L)
            );
            String jobId = service.startRollingOptionJob(config);
            DownloadJobStats stats = service.runJob(jobId);
            assertEquals(1L, stats.completedTasks());
            assertTrue(stats.rowsWritten() > 0L);

            var bars = warehouse.queryRollingOptionBars(
                    "NIFTY", "MONTH", 1, 0, "CALL", 5,
                    1_704_067_000_000L, 1_738_000_000_000L, 100
            );
            assertTrue(bars.size() > 0);
        }
    }

    private static void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
