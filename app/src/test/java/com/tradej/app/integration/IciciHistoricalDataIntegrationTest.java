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
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class IciciHistoricalDataIntegrationTest {
    private static IciciMarketDataProvider marketDataProvider;
    private static BreezeInstrumentResolver instrumentResolver;

    @BeforeAll
    static void setUp() throws Exception {
        BreezeConnectionSettings settings = LiveIciciTestSupport.connectionSettingsOrSkip();
        Files.deleteIfExists(settings.tokenStateFile());

        BreezeTokenManager tokenManager = new BreezeTokenManager(settings);
        tokenManager.ensureValid();
        BreezeAuthenticatedHttpClient httpClient = new BreezeAuthenticatedHttpClient(tokenManager);
        instrumentResolver = new BreezeInstrumentResolver(new BreezeInstrumentLoader());
        loadSecurityMaster(instrumentResolver);
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
        marketDataProvider = new IciciMarketDataProvider(
                new BreezeMarketDataRestClient(httpClient),
                historicalDataService,
                instrumentResolver,
                mapper
        );
    }

    private static void loadSecurityMaster(BreezeInstrumentResolver resolver) throws Exception {
        Path cached = Path.of("/tmp/SecurityMaster.zip");
        if (Files.exists(cached)) {
            resolver.loadCatalog(cached);
        } else {
            resolver.loadFromRemote();
        }
        assertFalse(resolver.allInstruments().isEmpty(), "ICICI SecurityMaster catalog must not be empty");
    }

    @Test
    void resolvesRelianceByCommonSymbolAliases() {
        InstrumentKey reliance = resolveEquity("RELIANCE");
        InstrumentKey relind = resolveEquity("RELIND");
        assertNotNull(instrumentResolver.getBySymbol(reliance));
        assertNotNull(instrumentResolver.getBySymbol(relind));
        assertEquals("RELIANCE", instrumentResolver.requireDefinition(reliance).canonicalSymbol());
        assertEquals("RELIANCE", instrumentResolver.requireDefinition(relind).canonicalSymbol());
    }

    @Test
    void fetchesDailyCandlesForReliance() {
        InstrumentKey key = resolveEquity("RELIANCE");
        LocalDate end = latestTradingDate();
        var candles = marketDataProvider.getCandles(new CandleHistoryRequest(
                key,
                "1d",
                end.minusDays(10),
                end
        ));
        assertFalse(candles.isEmpty(), "Expected at least one daily candle for RELIANCE");
        assertEquals("RELIANCE", candles.getFirst().symbol());
    }

    @Test
    void fetchesOneMinuteCandlesForReliance() {
        InstrumentKey key = resolveEquity("RELIANCE");
        LocalDate day = latestTradingDate();
        var candles = marketDataProvider.getCandles(new CandleHistoryRequest(
                key,
                "1m",
                day,
                day
        ));
        assertFalse(candles.isEmpty(), "Expected at least one 1-minute candle for RELIANCE on " + day);
        assertEquals("RELIANCE", candles.getFirst().symbol());
    }

    @Test
    void fetchesMultiDayOneMinuteCandlesForReliance() {
        InstrumentKey key = resolveEquity("RELIANCE");
        LocalDate end = latestTradingDate();
        LocalDate start = end.minusDays(4);
        var candles = marketDataProvider.getCandles(new CandleHistoryRequest(key, "1m", start, end));
        assertTrue(candles.size() > 100, "Expected multi-day 1m candles, got " + candles.size());
    }

    @Test
    void fetchesFiveMinuteCandlesForReliance() {
        InstrumentKey key = resolveEquity("RELIANCE");
        LocalDate day = latestTradingDate();
        var candles = marketDataProvider.getCandles(new CandleHistoryRequest(key, "5m", day, day));
        assertFalse(candles.isEmpty(), "Expected 5-minute candles for RELIANCE");
    }

    @Test
    void fetchesOneSecondCandlesForReliance() {
        InstrumentKey key = resolveEquity("RELIANCE");
        LocalDate day = latestTradingDate();
        var candles = marketDataProvider.getCandles(new CandleHistoryRequest(key, "1s", day, day));
        assertFalse(candles.isEmpty(), "Expected 1-second candles for RELIANCE on " + day);
        assertEquals("RELIANCE", candles.getFirst().symbol());
    }

    private static InstrumentKey resolveEquity(String symbol) {
        return instrumentResolver.resolveNormalized(symbol, ExchangeSegment.NSE_EQ).key();
    }

    private static LocalDate latestTradingDate() {
        ZoneId india = ZoneId.of("Asia/Kolkata");
        LocalDate date = LocalDate.now(india);
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.minusDays(2);
        }
        return date;
    }
}
