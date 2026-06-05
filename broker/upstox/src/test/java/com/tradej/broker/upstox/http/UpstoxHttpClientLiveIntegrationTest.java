package com.tradej.broker.upstox.http;

import com.tradej.broker.upstox.adapter.UpstoxMarketDataProvider;
import com.tradej.broker.upstox.auth.UpstoxAnalyticsTokenHolder;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxHistoricalDataRestClient;
import com.tradej.broker.upstox.rest.UpstoxMarketDataRestClient;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("upstox-preflight")
class UpstoxHttpClientLiveIntegrationTest {

    @Test
    void invalidAnalyticsTokenThrowsInsteadOfZeroLtp() throws Exception {
        UpstoxConnectionSettings settings = new UpstoxConnectionSettings(
                "dummy-client",
                "dummy-secret",
                "http://127.0.0.1:18080/callback",
                null,
                null,
                "invalid-analytics-token",
                null,
                true,
                false,
                18080,
                1_800_000L,
                600_000L
        );
        UpstoxAnalyticsTokenHolder tokenHolder = new UpstoxAnalyticsTokenHolder(settings);
        UpstoxJsonHttpClient jsonClient = new UpstoxJsonHttpClient(
                new UpstoxHttpClient(HttpClient.newHttpClient(), tokenHolder, "https://api.upstox.com/v2"));

        Path catalogDir = Files.createTempDirectory("upstox-invalid-token");
        UpstoxInstrumentResolver resolver = new UpstoxInstrumentResolver();
        new UpstoxInstrumentLoader(HttpClient.newHttpClient()).downloadAndLoad(catalogDir, resolver);

        UpstoxMarketDataProvider marketData = new UpstoxMarketDataProvider(
                new UpstoxMarketDataRestClient(jsonClient),
                resolver,
                new UpstoxHistoricalDataRestClient(jsonClient)
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> marketData.getLtpPaisa(new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ)));
        assertTrue(hasAuthFailure(ex), "Expected UpstoxApiException auth failure, got: " + ex);
    }

    private static boolean hasAuthFailure(Throwable ex) {
        while (ex != null) {
            if (ex instanceof UpstoxApiException api && api.isAuthFailure()) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }
}
