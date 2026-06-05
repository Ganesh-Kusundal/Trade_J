package com.tradej.app.integration;

import com.tradej.broker.upstox.adapter.UpstoxMarketDataProvider;
import com.tradej.broker.upstox.auth.UpstoxAnalyticsTokenHolder;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.broker.upstox.http.UpstoxHttpClient;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxHistoricalDataRestClient;
import com.tradej.broker.upstox.rest.UpstoxMarketDataRestClient;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.model.EquityHistoricalDownloadConfig;
import com.tradej.historical.ingest.query.EquityHistoricalQuery;
import com.tradej.historical.ingest.service.EquityDownloadJobService;
import com.tradej.historical.ingest.universe.Nifty500UniverseFetcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
@Tag("integration")
@Tag("upstox-preflight")
class UpstoxEquityBackfillLiveIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void fetchesNifty500UniverseOnline() throws Exception {
        assumeTrue(LiveUpstoxTestSupport.integrationEnabled());
        List<com.tradej.historical.ingest.universe.Nifty500Constituent> constituents =
                new Nifty500UniverseFetcher().fetch();
        assertTrue(constituents.size() >= 490, "Expected at least 490 Nifty 500 constituents");
        assertFalse(constituents.getFirst().industry().isBlank(), "Industry column should be populated");
    }

    @Test
    void downloadsTwoSymbolsToParquetAndQueriesThem() throws Exception {
        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.analyticsConnectionSettingsOrSkip();
        Path root = tempDir.resolve("historical-equity");

        HttpClient httpClient = HttpClient.newHttpClient();
        UpstoxJsonHttpClient jsonClient = new UpstoxJsonHttpClient(
                new UpstoxHttpClient(httpClient, new UpstoxAnalyticsTokenHolder(settings), "https://api.upstox.com/v2")
        );
        UpstoxInstrumentResolver resolver = new UpstoxInstrumentResolver();
        new UpstoxInstrumentLoader(httpClient).downloadAndLoad(tempDir.resolve("catalog"), resolver);

        UpstoxMarketDataProvider marketData = new UpstoxMarketDataProvider(
                new UpstoxMarketDataRestClient(jsonClient),
                resolver,
                new UpstoxHistoricalDataRestClient(jsonClient)
        );

        try (EquityDownloadJobService service = new EquityDownloadJobService(
                root,
                marketData,
                resolver,
                1,
                System::currentTimeMillis,
                () -> { },
                Nifty500UniverseFetcher.DEFAULT_UNIVERSE_URL
        )) {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(4);
            EquityHistoricalDownloadConfig config = new EquityHistoricalDownloadConfig(
                    List.of("TCS", "SBIN"),
                    ExchangeSegment.NSE_EQ,
                    from,
                    to,
                    "1m",
                    "interval=1m",
                    1,
                    root.toString(),
                    0L,
                    1,
                    false,
                    true
            );
            String jobId = service.startEquityJob(config);
            var stats = service.runJob(jobId);
            assertTrue(stats.completedTasks() >= 2, "Expected both symbol tasks to complete");
            assertTrue(stats.rowsWritten() > 0, "Expected parquet rows to be written");
        }

        try (EquityHistoricalQuery query = new EquityHistoricalQuery(root)) {
            assertTrue(query.countCandles("TCS") > 0, "Expected TCS 1m bars in parquet view");
            assertTrue(query.countCandles("SBIN") > 0, "Expected SBIN 1m bars in parquet view");
        }
    }
}
