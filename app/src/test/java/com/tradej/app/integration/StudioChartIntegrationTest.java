package com.tradej.app.integration;

import com.tradej.analytics.config.DuckDbAnalyticsConfig;
import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.analytics.repository.FederatedHistoricalBarRepository;
import com.tradej.app.studio.StudioChartService;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.indicators.IndicatorEngine;
import com.tradej.institutional.InstitutionalScanEngine;
import com.tradej.institutional.model.InstitutionalScanConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class StudioChartIntegrationTest {

    @Test
    void loadsTwentyDayChartAcrossMonthBoundary() throws Exception {
        Path root = HistoricalEquityPaths.root(Path.of(HistoricalEquityPaths.DEFAULT_ROOT));
        assumeTrue(Files.isDirectory(HistoricalEquityPaths.barsDir(root, "interval=1m")),
                "Parquet warehouse not present at " + root);

        try (DuckDbAnalyticsEngine engine = new DuckDbAnalyticsEngine(new DuckDbAnalyticsConfig(
                root,
                Path.of("runtime-dev/historical.duckdb"),
                Path.of("runtime-dev/trade.duckdb"),
                false,
                true,
                10_000,
                30_000L
        ))) {
            FederatedHistoricalBarRepository repository = new FederatedHistoricalBarRepository(engine);
            LocalDate scanDate = repository.latestAvailableTradingDay(30)
                    .orElseThrow(() -> new IllegalStateException("No trading day"));
            LocalDate from = scanDate.minusDays(19);
            InstitutionalScanEngine scanEngine = new InstitutionalScanEngine(repository, InstitutionalScanConfig.baseline());
            StudioChartService service = new StudioChartService(
                    repository,
                    new com.tradej.analytics.repository.DuckDbRollingOptionHistoricalRepository(engine),
                    scanEngine,
                    new IndicatorEngine()
            );

            Map<String, Object> startup = service.startupCandidates(java.util.Optional.empty(), 3);
            assertEquals(20, startup.get("chartLookbackDays"));

            Map<String, Object> singleDay = service.chartPayload(
                    "SBIN", ExchangeSegment.NSE_EQ, "5m", scanDate, scanDate);
            Map<String, Object> twentyDay = service.chartPayload(
                    "SBIN", ExchangeSegment.NSE_EQ, "5m", from, scanDate);

            int singleCount = (int) singleDay.get("count");
            int rangeCount = (int) twentyDay.get("count");
            assertTrue(rangeCount > singleCount, "Expected wider range to return more candles");
            assertEquals(from.toString(), twentyDay.get("from"));
            assertEquals(scanDate.toString(), twentyDay.get("to"));
        }
    }
}
