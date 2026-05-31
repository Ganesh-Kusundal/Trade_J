package com.tradej.app.integration;

import com.tradej.analytics.config.DuckDbAnalyticsConfig;
import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.analytics.repository.DuckDbRollingOptionHistoricalRepository;
import com.tradej.analytics.repository.FederatedHistoricalBarRepository;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class AnalyticsFederationIntegrationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void federatedEquityAndOptionsQueriesAgainstLocalWarehouse() throws Exception {
        Path equityRoot = HistoricalEquityPaths.root(Path.of(HistoricalEquityPaths.DEFAULT_ROOT));
        Path optionsWarehouse = Path.of("runtime-dev/historical.duckdb");
        assumeTrue(Files.isDirectory(HistoricalEquityPaths.barsDir(equityRoot, "interval=1m")),
                "Equity parquet warehouse not present");
        assumeTrue(Files.isRegularFile(optionsWarehouse), "Options warehouse not present");

        try (DuckDbAnalyticsEngine engine = new DuckDbAnalyticsEngine(new DuckDbAnalyticsConfig(
                equityRoot,
                optionsWarehouse,
                Path.of("runtime-dev/trade.duckdb"),
                false,
                true,
                1000,
                30_000L
        ))) {
            FederatedHistoricalBarRepository equityRepo = new FederatedHistoricalBarRepository(engine);
            DuckDbRollingOptionHistoricalRepository optionRepo = new DuckDbRollingOptionHistoricalRepository(engine);

            LocalDate scanDate = equityRepo.latestAvailableTradingDay(30)
                    .orElseThrow(() -> new IllegalStateException("No equity trading day"));
            var candles = equityRepo.queryCandles(
                    com.tradej.core.domain.model.InstrumentKey.of("SBIN", ExchangeSegment.NSE_EQ),
                    "5m",
                    scanDate,
                    scanDate
            );
            assertFalse(candles.isEmpty(), "Expected SBIN candles on " + scanDate);

            if (engine.optionsAttached()) {
                long fromMs = scanDate.atStartOfDay(IST).toInstant().toEpochMilli();
                long toMs = scanDate.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli();
                var optionBars = optionRepo.queryBars(new RollingOptionSeriesRequest(
                        "NIFTY", "WEEK", 1, 0, OptionType.CALL, 5, fromMs, toMs, 100
                ));
                assertTrue(optionBars.size() >= 0);

                var joinResult = engine.executeReadOnlySql("""
                        select count(*) as joined_rows
                        from equity_bars_1m e
                        join rolling_option_bars o
                          on o.underlying = 'NIFTY' and o.bar_time_ms = e.bar_time_ms
                        where e.symbol = 'SBIN'
                        """, 10);
                assertFalse(joinResult.rows().isEmpty());
            }
        }
    }
}
