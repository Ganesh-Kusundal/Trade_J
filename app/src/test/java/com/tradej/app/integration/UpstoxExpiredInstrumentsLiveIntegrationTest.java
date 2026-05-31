package com.tradej.app.integration;

import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.upstox.auth.UpstoxStaticTokenHolder;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionMapper;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionService;
import com.tradej.broker.upstox.http.UpstoxHttpClient;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.resilience.UpstoxResilienceExecutor;
import com.tradej.broker.upstox.rest.UpstoxExpiredInstrumentRestClient;
import com.tradej.broker.upstox.http.UpstoxApiException;
import com.tradej.core.domain.instrument.ExpiredOptionContractKey;
import com.tradej.core.domain.model.ExpiredOptionBar;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("upstox-preflight")
class UpstoxExpiredInstrumentsLiveIntegrationTest {

    @Test
    void expiredInstrumentsPreflightForNiftyIndex() throws Exception {
        UpstoxExpiredOptionService service = buildService();
        List<LocalDate> expiries = listExpiriesOrSkip(service);
        assertFalse(expiries.isEmpty(), "Expected expired expiries for NIFTY via analytics Plus token");
    }

    @Test
    void expiredOptionsEndToEndChain() throws Exception {
        UpstoxExpiredOptionService service = buildService();

        List<LocalDate> expiries = listExpiriesOrSkip(service);
        assertFalse(expiries.isEmpty());
        LocalDate expiry = expiries.getLast();

        List<ExpiredOptionContractKey> contracts = service.listContracts("NIFTY", ExchangeSegment.IDX_I, expiry);
        assertFalse(contracts.isEmpty(), "Expected CE/PE contracts for expiry " + expiry);
        assertTrue(contracts.stream().anyMatch(c -> c.optionType() == OptionType.CALL));
        assertTrue(contracts.stream().anyMatch(c -> c.optionType() == OptionType.PUT));

        ExpiredOptionContractKey contract = contracts.getFirst();
        LocalDate to = expiry;
        LocalDate from = expiry.minusDays(7);
        List<ExpiredOptionBar> bars = service.fetchCandles(contract, "5minute", from, to);
        assertFalse(bars.isEmpty(), "Expected 5minute candles for expired contract " + contract.brokerInstrumentKey());
        assertTrue(bars.getFirst().closePaisa() >= 0);
    }

    private static UpstoxExpiredOptionService buildService() throws Exception {
        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.plusExpiredConnectionSettingsOrSkip();
        UpstoxStaticTokenHolder tokenHolder = new UpstoxStaticTokenHolder(
                settings.accessToken(),
                false,
                "Upstox access token (Plus expired instruments)");
        HttpClient httpClient = HttpClient.newHttpClient();
        UpstoxJsonHttpClient jsonClient = new UpstoxJsonHttpClient(
                new UpstoxHttpClient(httpClient, tokenHolder, "https://api.upstox.com/v2"));

        MultiBucketRateLimiter rateLimiter = new MultiBucketRateLimiter(Map.of(
                "EXPIRED_INSTRUMENT", new RateLimitConfig("EXPIRED_INSTRUMENT", 3.0, 2)));
        UpstoxResilienceExecutor resilience = new UpstoxResilienceExecutor(rateLimiter, new CircuitBreaker());
        UpstoxExpiredInstrumentRestClient restClient =
                new UpstoxExpiredInstrumentRestClient(jsonClient, resilience);

        UpstoxInstrumentResolver resolver = new UpstoxInstrumentResolver();
        Path catalogDir = LiveUpstoxTestSupport.propertiesPath("runtime/upstox-instruments-test");
        Files.createDirectories(catalogDir);
        Path cached = catalogDir.resolve("complete.json.gz");
        UpstoxInstrumentLoader loader = new UpstoxInstrumentLoader(httpClient);
        if (Files.exists(cached)) {
            loader.loadFromPath(cached, resolver);
        } else {
            loader.downloadAndLoad(catalogDir, resolver);
        }
        assertNotNull(resolver.requireInstrumentKey(
                new com.tradej.core.domain.model.InstrumentKey("NIFTY", ExchangeSegment.IDX_I)));

        return new UpstoxExpiredOptionService(
                restClient,
                new UpstoxExpiredOptionMapper(),
                resolver
        );
    }

    private static List<LocalDate> listExpiriesOrSkip(UpstoxExpiredOptionService service) {
        try {
            return service.listExpiries("NIFTY", ExchangeSegment.IDX_I);
        } catch (UpstoxApiException ex) {
            if (ex.httpStatus() == 401 || ex.httpStatus() == 403) {
                Assumptions.assumeTrue(false,
                        "Expired instruments API requires Upstox Plus access token (upstox.live.accessToken): "
                                + ex.getMessage());
            }
            throw ex;
        }
    }
}
