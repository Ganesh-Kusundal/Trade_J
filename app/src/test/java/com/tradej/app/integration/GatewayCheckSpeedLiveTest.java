package com.tradej.app.integration;

import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import com.tradej.broker.icici.adapter.IciciMarketDataProvider;
import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.historical.BreezeHistoricalDataService;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;
import com.tradej.broker.icici.instrument.BreezeInstrumentLoader;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.resilience.IciciResilienceExecutor;
import com.tradej.broker.icici.rest.BreezeHistoricalRestClient;
import com.tradej.broker.icici.rest.BreezeMarketDataRestClient;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.tradej.broker.icici.config.IciciAuthMode;

public class GatewayCheckSpeedLiveTest {
    
    @Test
    public void testLiveSpeed() throws Exception {
        System.out.println("=========================================================================");
        System.out.println("       STARTING LIVE SPEED CHECK (ICICI API)                              ");
        System.out.println("=========================================================================");
        
        System.setProperty("trade.workspace.root", "/Users/apple/Downloads/Trade_J");
        
        java.util.Properties p = new java.util.Properties();
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Path.of("/Users/apple/Downloads/Trade_J/config/icici-local.properties"))) {
            p.load(in);
        }
        
        BreezeConnectionSettings settings = BreezeConnectionSettings.withDefaults(
                p.getProperty("icici.appKey"),
                p.getProperty("icici.secretKey"),
                p.getProperty("icici.sessionToken"),
                IciciAuthMode.valueOf(p.getProperty("icici.authMode", "BROWSER_AUTOMATED")),
                Path.of("/Users/apple/Downloads/Trade_J", p.getProperty("icici.totpSecretFile", "config/icici-totp-secret.txt")),
                Path.of("/Users/apple/Downloads/Trade_J", p.getProperty("icici.usernameFile", "config/icici-username.txt")),
                Path.of("/Users/apple/Downloads/Trade_J", p.getProperty("icici.passwordFile", "config/icici-password.txt")),
                Path.of("/Users/apple/Downloads/Trade_J", p.getProperty("icici.apiSessionFile", "config/icici-api-session.txt")),
                Path.of("/Users/apple/Downloads/Trade_J", p.getProperty("icici.tokenStateFile", "runtime/icici-token-state.json")),
                Boolean.parseBoolean(p.getProperty("icici.ordersEnabled", "false")),
                Long.parseLong(p.getProperty("icici.refreshBufferMinutes", "10")),
                Integer.parseInt(p.getProperty("icici.loginRedirectPort", "9080")),
                p.getProperty("icici.loginRedirectPath", "/api"),
                Boolean.parseBoolean(p.getProperty("icici.browserHeadless", "true")),
                Long.parseLong(p.getProperty("icici.browserLoginTimeoutSeconds", "120"))
        );
        
        BreezeTokenManager tokenManager = new BreezeTokenManager(settings);
        tokenManager.ensureValid();
        BreezeAuthenticatedHttpClient httpClient = new BreezeAuthenticatedHttpClient(tokenManager);
        BreezeInstrumentResolver instrumentResolver = new BreezeInstrumentResolver(new BreezeInstrumentLoader());
        
        Path cached = Path.of("/tmp/SecurityMaster.zip");
        if (Files.exists(cached)) {
            instrumentResolver.loadCatalog(cached);
        } else {
            instrumentResolver.loadFromRemote();
        }

        BreezeDomainMapper mapper = new BreezeDomainMapper();
        MultiBucketRateLimiter rateLimiter = new MultiBucketRateLimiter(Map.of(
                IciciResilienceExecutor.CATEGORY_DATA, new RateLimitConfig(IciciResilienceExecutor.CATEGORY_DATA, 100.0 / 60.0, 100),
                IciciResilienceExecutor.CATEGORY_DAILY, new RateLimitConfig(IciciResilienceExecutor.CATEGORY_DAILY, 5000.0 / 86400.0, 5000)
        ));
        BreezeHistoricalDataService historicalDataService = new BreezeHistoricalDataService(
                new BreezeHistoricalRestClient(httpClient),
                mapper,
                new IciciResilienceExecutor(rateLimiter)
        );
        IciciMarketDataProvider marketData = new IciciMarketDataProvider(
                new BreezeMarketDataRestClient(httpClient),
                historicalDataService,
                instrumentResolver,
                mapper
        );

        String[] symbols = {"SBIN", "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "AXISBANK", "KOTAKBANK", "LT", "ITC"};
        List<InstrumentKey> keys = new ArrayList<>();
        for (String sym : symbols) {
            keys.add(instrumentResolver.resolveNormalized(sym, ExchangeSegment.NSE_EQ).key());
        }
        
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);
        
        System.out.println("Simulation Configuration:");
        System.out.println(" - Symbols count: " + keys.size() + " " + java.util.Arrays.toString(symbols));
        System.out.println(" - Days of history: 30 (" + from + " to " + to + ")");
        System.out.println(" - Interval: 5m");
        System.out.println("=========================================================================");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (InstrumentKey key : keys) {
            futures.add(CompletableFuture.runAsync(() -> {
                long reqStart = System.currentTimeMillis();
                try {
                    List<Candle> candles = marketData.getCandles(new CandleHistoryRequest(key, "5m", from, to));
                    long latency = System.currentTimeMillis() - reqStart;
                    System.out.printf("[SUCCESS] Fetched %d candles for %s in %d ms\n", candles.size(), key.symbol(), latency);
                } catch (Exception e) {
                    long latency = System.currentTimeMillis() - reqStart;
                    System.out.printf("[ERROR] Failed to fetch for %s after %d ms: %s\n", key.symbol(), latency, e.getMessage());
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long totalDurationMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        System.out.println("=========================================================================");
        System.out.printf("TOTAL TIME TO FETCH ALL DATA: %d ms (%.2f seconds)\n", totalDurationMs, totalDurationMs / 1000.0);
        System.out.println("=========================================================================");
    }
}
