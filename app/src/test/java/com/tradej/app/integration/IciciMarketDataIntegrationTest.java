package com.tradej.app.integration;

import com.tradej.broker.icici.IciciBrokerConnection;
import com.tradej.broker.icici.adapter.IciciMarketDataProvider;
import com.tradej.broker.icici.adapter.IciciPortfolioProvider;
import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;
import com.tradej.broker.icici.instrument.BreezeInstrumentLoader;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.historical.BreezeHistoricalDataService;
import com.tradej.broker.icici.resilience.IciciResilienceExecutor;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import java.util.Map;
import com.tradej.broker.icici.rest.BreezeMarketDataRestClient;
import com.tradej.broker.icici.rest.BreezePortfolioRestClient;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class IciciMarketDataIntegrationTest {
    private static IciciMarketDataProvider marketDataProvider;
    private static IciciPortfolioProvider portfolioProvider;
    private static BreezeInstrumentResolver instrumentResolver;

    @BeforeAll
    static void setUp() throws Exception {
        BreezeConnectionSettings settings = LiveIciciTestSupport.connectionSettingsOrSkip();
        LiveIciciTestSupport.preflightSessionOrSkip(settings);
        BreezeTokenManager tokenManager = new BreezeTokenManager(settings);
        BreezeAuthenticatedHttpClient httpClient = new BreezeAuthenticatedHttpClient(tokenManager);
        instrumentResolver = new BreezeInstrumentResolver(new BreezeInstrumentLoader());
        instrumentResolver.loadFromRemote();
        BreezeDomainMapper mapper = new BreezeDomainMapper();
        MultiBucketRateLimiter rateLimiter = new MultiBucketRateLimiter(Map.of(
                IciciResilienceExecutor.CATEGORY_DATA, new RateLimitConfig(IciciResilienceExecutor.CATEGORY_DATA, 100.0 / 60.0, 100),
                IciciResilienceExecutor.CATEGORY_DAILY, new RateLimitConfig(IciciResilienceExecutor.CATEGORY_DAILY, 5000.0 / 86400.0, 5000)
        ));
        BreezeHistoricalDataService historicalDataService = new BreezeHistoricalDataService(
                new com.tradej.broker.icici.rest.BreezeHistoricalRestClient(httpClient),
                mapper,
                new IciciResilienceExecutor(rateLimiter)
        );
        marketDataProvider = new IciciMarketDataProvider(
                new BreezeMarketDataRestClient(httpClient),
                historicalDataService,
                instrumentResolver,
                mapper
        );
        portfolioProvider = new IciciPortfolioProvider(new BreezePortfolioRestClient(httpClient), new BreezeInstrumentResolver());
    }

    @Test
    void fetchesQuoteForReliance() {
        InstrumentKey key = resolveEquity("RELIANCE");
        long ltp = marketDataProvider.getLtpPaisa(key);
        assertTrue(ltp > 0, "Expected positive LTP for RELIANCE");
    }

    @Test
    void fetchesFundsBalance() {
        var balance = portfolioProvider.getBalance();
        assertTrue(balance.cashPaisa() >= 0);
    }

    private static InstrumentKey resolveEquity(String symbol) {
        InstrumentKey key = new InstrumentKey(symbol, ExchangeSegment.NSE_EQ);
        if (instrumentResolver.getBySymbol(key) != null) {
            return key;
        }
        return instrumentResolver.allInstruments().stream()
                .filter(i -> symbol.equalsIgnoreCase(i.symbol()))
                .map(com.tradej.core.domain.model.Instrument::key)
                .findFirst()
                .orElse(key);
    }
}
