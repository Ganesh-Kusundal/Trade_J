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
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("upstox-preflight")
class UpstoxHistoricalDataLiveIntegrationTest {

    @Test
    void analyticsTokenFetchesHistoricalDailyCandlesForSbin() throws Exception {
        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.analyticsConnectionSettingsOrSkip();
        UpstoxAnalyticsTokenHolder tokenHolder = new UpstoxAnalyticsTokenHolder(settings);
        HttpClient httpClient = HttpClient.newHttpClient();
        UpstoxJsonHttpClient upstoxJson = new UpstoxJsonHttpClient(
                new UpstoxHttpClient(httpClient, tokenHolder, "https://api.upstox.com/v2"));

        Path catalogDir = Files.createTempDirectory("upstox-catalog");
        UpstoxInstrumentResolver resolver = new UpstoxInstrumentResolver();
        new UpstoxInstrumentLoader(httpClient).downloadAndLoad(catalogDir, resolver);

        UpstoxMarketDataProvider marketData = new UpstoxMarketDataProvider(
                new UpstoxMarketDataRestClient(upstoxJson),
                resolver,
                new UpstoxHistoricalDataRestClient(upstoxJson)
        );

        InstrumentKey sbin = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(5);
        List<Candle> candles = marketData.getCandles(new CandleHistoryRequest(sbin, "1d", from, to));

        assertFalse(candles.isEmpty(), "Expected daily historical candles for SBIN via analytics token");
        assertTrue(candles.getFirst().closePaisa() > 0, "Candle close should be positive");
    }

    @Test
    void analyticsTokenFetchesLtpForSbin() {
        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.analyticsConnectionSettingsOrSkip();
        UpstoxAnalyticsTokenHolder tokenHolder = new UpstoxAnalyticsTokenHolder(settings);
        UpstoxJsonHttpClient upstoxJson = new UpstoxJsonHttpClient(
                new UpstoxHttpClient(HttpClient.newHttpClient(), tokenHolder, "https://api.upstox.com/v2"));

        Path catalogDir = LiveUpstoxTestSupport.propertiesPath("runtime/upstox-instruments-test");
        try {
            Files.createDirectories(catalogDir);
        } catch (Exception ignored) {
        }
        UpstoxInstrumentResolver resolver = new UpstoxInstrumentResolver();
        try {
            Path cached = catalogDir.resolve("complete.json.gz");
            if (Files.exists(cached)) {
                new UpstoxInstrumentLoader(HttpClient.newHttpClient()).loadFromPath(cached, resolver);
            } else {
                new UpstoxInstrumentLoader(HttpClient.newHttpClient()).downloadAndLoad(catalogDir, resolver);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load Upstox instrument catalog", ex);
        }

        UpstoxMarketDataProvider marketData = new UpstoxMarketDataProvider(
                new UpstoxMarketDataRestClient(upstoxJson),
                resolver,
                new UpstoxHistoricalDataRestClient(upstoxJson)
        );

        long ltp = marketData.getLtpPaisa(new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ));
        assertTrue(ltp > 0, "Expected positive LTP for SBIN");
    }
}
