package com.tradej.app.integration;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import com.tradej.broker.upstox.adapter.UpstoxNewsProvider;
import com.tradej.broker.upstox.auth.UpstoxAnalyticsTokenHolder;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.broker.upstox.http.UpstoxHttpClient;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxNewsRestClient;
import com.tradej.core.domain.model.NewsArticle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class UpstoxNewsIntegrationTest {

    private UpstoxBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void fetchesNewsByInstrumentKeys() throws Exception {
        Assumptions.assumeTrue(LiveUpstoxTestSupport.analyticsIntegrationEnabled(),
                "Upstox analytics integration is not enabled. Skip test.");

        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.analyticsConnectionSettingsOrSkip();
        UpstoxAnalyticsTokenHolder tokenHolder = new UpstoxAnalyticsTokenHolder(settings);
        HttpClient httpClient = HttpClient.newHttpClient();
        String baseUrl = "https://api.upstox.com/v2";

        UpstoxHttpClient authenticatedClient = new UpstoxHttpClient(httpClient, tokenHolder, baseUrl);
        UpstoxJsonHttpClient jsonClient = new UpstoxJsonHttpClient(authenticatedClient);

        UpstoxInstrumentResolver instrumentResolver = new UpstoxInstrumentResolver();
        UpstoxInstrumentLoader instrumentLoader = new UpstoxInstrumentLoader(httpClient);

        Path catalogDir = LiveUpstoxTestSupport.propertiesPath("runtime/upstox-instruments-test");
        System.out.println("[TEST-DEBUG] Loading instrument catalog from: " + catalogDir);
        try {
            Files.createDirectories(catalogDir);
        } catch (Exception ignored) {
        }
        try {
            Path cached = catalogDir.resolve("complete.json.gz");
            if (Files.exists(cached)) {
                System.out.println("[TEST-DEBUG] Loading catalog from local cache...");
                instrumentLoader.loadFromPath(cached, instrumentResolver);
            } else {
                System.out.println("[TEST-DEBUG] Cache not found, downloading catalog...");
                instrumentLoader.downloadAndLoad(catalogDir, instrumentResolver);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load Upstox instrument catalog", ex);
        }

        System.out.println("[TEST-DEBUG] Catalog loaded. Total size: " + instrumentResolver.catalogSize());

        // Test with known NSE equity symbols
        String instrumentKeys = "NSE_EQ|INE040H01021,NSE_EQ|INE002A01018"; // HDFC Bank, Reliance
        System.out.println("[TEST-DEBUG] Fetching news for instrument keys: " + instrumentKeys);

        NewsProvider newsProvider = new UpstoxNewsProvider(
                new UpstoxNewsRestClient(jsonClient)
        );

        List<NewsArticle> articles = newsProvider.getNewsByInstrumentKeys(instrumentKeys, 1, 10);

        System.out.println("[TEST-DEBUG] Received " + articles.size() + " news articles");
        for (NewsArticle article : articles) {
            System.out.println("[TEST-DEBUG] Article: " + article.instrumentKey() + " - " + article.heading());
            System.out.println("[TEST-DEBUG]   Summary: " + article.summary());
            System.out.println("[TEST-DEBUG]   Link: " + article.articleLink());
            System.out.println("[TEST-DEBUG]   Published: " + article.publishedAt());
        }

        assertNotNull(articles, "News articles list should not be null");
        // Note: We don't assert on size as there may be no news for these instruments in the past 7 days
        // But we verify the call succeeds and returns a valid structure
        for (NewsArticle article : articles) {
            assertNotNull(article.instrumentKey(), "Instrument key should not be null");
            assertNotNull(article.heading(), "Heading should not be null");
            assertNotNull(article.summary(), "Summary should not be null");
            assertNotNull(article.articleLink(), "Article link should not be null");
            assertTrue(article.publishedTimeMs() > 0, "Published time should be positive");
        }
    }

    @Test
    void fetchesNewsForPositions() throws Exception {
        Assumptions.assumeTrue(LiveUpstoxTestSupport.analyticsIntegrationEnabled(),
                "Upstox analytics integration is not enabled. Skip test.");

        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.analyticsConnectionSettingsOrSkip();
        UpstoxAnalyticsTokenHolder tokenHolder = new UpstoxAnalyticsTokenHolder(settings);
        HttpClient httpClient = HttpClient.newHttpClient();
        String baseUrl = "https://api.upstox.com/v2";

        UpstoxHttpClient authenticatedClient = new UpstoxHttpClient(httpClient, tokenHolder, baseUrl);
        UpstoxJsonHttpClient jsonClient = new UpstoxJsonHttpClient(authenticatedClient);

        NewsProvider newsProvider = new UpstoxNewsProvider(
                new UpstoxNewsRestClient(jsonClient)
        );

        System.out.println("[TEST-DEBUG] Fetching news for positions...");
        List<NewsArticle> articles = newsProvider.getNewsForPositions(1, 10);

        System.out.println("[TEST-DEBUG] Received " + articles.size() + " news articles for positions");
        for (NewsArticle article : articles) {
            System.out.println("[TEST-DEBUG] Article: " + article.instrumentKey() + " - " + article.heading());
        }

        assertNotNull(articles, "News articles list should not be null");
        for (NewsArticle article : articles) {
            assertNotNull(article.instrumentKey(), "Instrument key should not be null");
            assertNotNull(article.heading(), "Heading should not be null");
            assertNotNull(article.summary(), "Summary should not be null");
            assertNotNull(article.articleLink(), "Article link should not be null");
            assertTrue(article.publishedTimeMs() > 0, "Published time should be positive");
        }
    }

    @Test
    void fetchesNewsForHoldings() throws Exception {
        Assumptions.assumeTrue(LiveUpstoxTestSupport.analyticsIntegrationEnabled(),
                "Upstox analytics integration is not enabled. Skip test.");

        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.analyticsConnectionSettingsOrSkip();
        UpstoxAnalyticsTokenHolder tokenHolder = new UpstoxAnalyticsTokenHolder(settings);
        HttpClient httpClient = HttpClient.newHttpClient();
        String baseUrl = "https://api.upstox.com/v2";

        UpstoxHttpClient authenticatedClient = new UpstoxHttpClient(httpClient, tokenHolder, baseUrl);
        UpstoxJsonHttpClient jsonClient = new UpstoxJsonHttpClient(authenticatedClient);

        NewsProvider newsProvider = new UpstoxNewsProvider(
                new UpstoxNewsRestClient(jsonClient)
        );

        System.out.println("[TEST-DEBUG] Fetching news for holdings...");
        List<NewsArticle> articles = newsProvider.getNewsForHoldings(1, 10);

        System.out.println("[TEST-DEBUG] Received " + articles.size() + " news articles for holdings");
        for (NewsArticle article : articles) {
            System.out.println("[TEST-DEBUG] Article: " + article.instrumentKey() + " - " + article.heading());
        }

        assertNotNull(articles, "News articles list should not be null");
        for (NewsArticle article : articles) {
            assertNotNull(article.instrumentKey(), "Instrument key should not be null");
            assertNotNull(article.heading(), "Heading should not be null");
            assertNotNull(article.summary(), "Summary should not be null");
            assertNotNull(article.articleLink(), "Article link should not be null");
            assertTrue(article.publishedTimeMs() > 0, "Published time should be positive");
        }
    }

    @Test
    void fetchesNewsViaGateway() throws Exception {
        Assumptions.assumeTrue(LiveUpstoxTestSupport.analyticsIntegrationEnabled(),
                "Upstox analytics integration is not enabled. Skip test.");

        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.analyticsConnectionSettingsOrSkip();
        UpstoxAnalyticsTokenHolder tokenHolder = new UpstoxAnalyticsTokenHolder(settings);
        HttpClient httpClient = HttpClient.newHttpClient();
        String baseUrl = "https://api.upstox.com/v2";

        UpstoxHttpClient authenticatedClient = new UpstoxHttpClient(httpClient, tokenHolder, baseUrl);
        UpstoxJsonHttpClient jsonClient = new UpstoxJsonHttpClient(authenticatedClient);

        UpstoxInstrumentResolver instrumentResolver = new UpstoxInstrumentResolver();
        UpstoxInstrumentLoader instrumentLoader = new UpstoxInstrumentLoader(httpClient);

        Path catalogDir = LiveUpstoxTestSupport.propertiesPath("runtime/upstox-instruments-test");
        try {
            Files.createDirectories(catalogDir);
        } catch (Exception ignored) {
        }
        try {
            Path cached = catalogDir.resolve("complete.json.gz");
            if (Files.exists(cached)) {
                instrumentLoader.loadFromPath(cached, instrumentResolver);
            } else {
                instrumentLoader.downloadAndLoad(catalogDir, instrumentResolver);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load Upstox instrument catalog", ex);
        }

        // Build full broker connection with news provider
        brokerConnection = new UpstoxBrokerConnection(
                new com.tradej.broker.upstox.adapter.UpstoxMarketDataProvider(
                        new com.tradej.broker.upstox.rest.UpstoxMarketDataRestClient(jsonClient),
                        instrumentResolver,
                        new com.tradej.broker.upstox.rest.UpstoxHistoricalDataRestClient(jsonClient)
                ),
                new com.tradej.broker.api.port.OrderCommand() {
                    @Override public com.tradej.core.domain.model.Order placeOrder(com.tradej.core.domain.model.OrderRequest request) { throw new UnsupportedOperationException(); }
                    @Override public com.tradej.core.domain.model.OrderPreview previewOrder(com.tradej.core.domain.model.OrderRequest request) { throw new UnsupportedOperationException(); }
                    @Override public com.tradej.core.domain.model.Order modifyOrder(com.tradej.core.domain.model.ModifyOrderRequest request) { throw new UnsupportedOperationException(); }
                    @Override public boolean cancelOrder(String orderId) { throw new UnsupportedOperationException(); }
                    @Override public java.util.List<String> cancelAllOpenOrders() { throw new UnsupportedOperationException(); }
                    @Override public java.util.List<String> cancelAndSquareOffIntradayPositions() { throw new UnsupportedOperationException(); }
                    @Override public boolean setKillSwitch(boolean enabled) { throw new UnsupportedOperationException(); }
                },
                new com.tradej.broker.api.port.OrderQuery() {
                    @Override public com.tradej.core.domain.model.Order getOrder(String orderId) { throw new UnsupportedOperationException(); }
                    @Override public java.util.List<com.tradej.core.domain.model.Order> getOrderBook() { throw new UnsupportedOperationException(); }
                    @Override public java.util.List<com.tradej.core.domain.model.Trade> getTradeBook() { throw new UnsupportedOperationException(); }
                    @Override public com.tradej.core.domain.value.OrderStatus getOrderStatus(String orderId) { throw new UnsupportedOperationException(); }
                    @Override public java.util.OptionalLong getExecutedPricePaisa(String orderId) { throw new UnsupportedOperationException(); }
                    @Override public java.util.OptionalLong getExchangeTimeMs(String orderId) { throw new UnsupportedOperationException(); }
                },
                new com.tradej.broker.api.port.PortfolioProvider() {
                    @Override public java.util.List<com.tradej.core.domain.model.Position> getPositions() { throw new UnsupportedOperationException(); }
                    @Override public java.util.List<com.tradej.core.domain.model.Holding> getHoldings() { throw new UnsupportedOperationException(); }
                    @Override public com.tradej.core.domain.model.Balance getBalance() { throw new UnsupportedOperationException(); }
                },
                (order) -> { throw new UnsupportedOperationException(); },
                instrumentResolver,
                new com.tradej.broker.upstox.websocket.UpstoxWebSocketMultiplexer(
                        new com.tradej.broker.upstox.websocket.UpstoxFeedAuthorizer(authenticatedClient),
                        new com.tradej.broker.upstox.websocket.UpstoxStreamNormalizer(instrumentResolver, new com.tradej.core.domain.event.EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock())),
                        instrumentResolver,
                        new com.tradej.core.domain.event.EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock()),
                        null
                ),
                new com.tradej.broker.upstox.adapter.UpstoxFuturesProvider(instrumentResolver),
                new com.tradej.broker.upstox.adapter.UpstoxOptionsProvider(
                        new com.tradej.broker.upstox.rest.UpstoxOptionChainRestClient(jsonClient),
                        instrumentResolver
                ),
                 new UpstoxNewsProvider(new UpstoxNewsRestClient(jsonClient)),
                 new com.tradej.broker.api.port.ConditionalAlertProvider() {
                     @Override public String placeAlert(com.tradej.core.domain.model.ConditionalAlertRequest request) { throw new UnsupportedOperationException(); }
                     @Override public com.tradej.core.domain.model.ConditionalAlert getAlert(String alertId) { throw new UnsupportedOperationException(); }
                     @Override public java.util.List<com.tradej.core.domain.model.ConditionalAlert> listAlerts() { throw new UnsupportedOperationException(); }
                     @Override public boolean deleteAlert(String alertId) { throw new UnsupportedOperationException(); }
                 }, // conditionalAlertProvider - not needed for news test
                null, // sliceOrderCommand - not needed for news test
                null, // dataServicesProvider - not needed for news test
                null, // profileProvider - not needed for news test
                instrumentLoader
        );

        // Test news accessor via IBrokerConnection interface
        String instrumentKeys = "NSE_EQ|INE040H01021";
        System.out.println("[TEST-DEBUG] Fetching news via gateway accessor for: " + instrumentKeys);

        List<NewsArticle> articles = brokerConnection.news().getNewsByInstrumentKeys(instrumentKeys, 1, 10);

        System.out.println("[TEST-DEBUG] Received " + articles.size() + " news articles via gateway");
        assertNotNull(articles, "News articles list should not be null");
        for (NewsArticle article : articles) {
            assertNotNull(article.instrumentKey(), "Instrument key should not be null");
            assertNotNull(article.heading(), "Heading should not be null");
        }
    }
}