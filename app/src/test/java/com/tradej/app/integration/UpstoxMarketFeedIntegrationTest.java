package com.tradej.app.integration;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import com.tradej.broker.upstox.adapter.UpstoxMarketDataProvider;
import com.tradej.broker.upstox.adapter.UpstoxFuturesProvider;
import com.tradej.broker.upstox.adapter.UpstoxNewsProvider;
import com.tradej.broker.upstox.adapter.UpstoxOptionsProvider;
import com.tradej.broker.upstox.auth.UpstoxAnalyticsTokenHolder;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.broker.upstox.http.UpstoxHttpClient;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxHistoricalDataRestClient;
import com.tradej.broker.upstox.rest.UpstoxMarketDataRestClient;
import com.tradej.broker.upstox.rest.UpstoxNewsRestClient;
import com.tradej.broker.upstox.rest.UpstoxOptionChainRestClient;
import com.tradej.broker.upstox.websocket.UpstoxFeedAuthorizer;
import com.tradej.broker.upstox.websocket.UpstoxStreamNormalizer;
import com.tradej.broker.upstox.websocket.UpstoxWebSocketMultiplexer;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-ws")
class UpstoxMarketFeedIntegrationTest {
    private UpstoxBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void receivesLiveTickFromUpstoxFeed() throws Exception {
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
        EventMetadataFactory metadataFactory = new EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock());

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

        InstrumentKey testKey = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);
        Instrument resolvedInstrument = instrumentResolver.resolve(testKey);
        Assumptions.assumeTrue(resolvedInstrument != null,
                "SBIN not resolved from catalog — instrument catalog may be empty or stale");
        String upstoxKey = instrumentResolver.requireInstrumentKey(testKey);
        System.out.println("[TEST-DEBUG] SBIN resolved to Upstox key: " + upstoxKey);

        MarketDataProvider marketData = new UpstoxMarketDataProvider(
                new UpstoxMarketDataRestClient(jsonClient),
                instrumentResolver,
                new UpstoxHistoricalDataRestClient(jsonClient)
        );

        UpstoxWebSocketMultiplexer webSocketMultiplexer = new UpstoxWebSocketMultiplexer(
                new UpstoxFeedAuthorizer(authenticatedClient),
                new UpstoxStreamNormalizer(instrumentResolver, metadataFactory),
                instrumentResolver,
                metadataFactory
        );

        FuturesProvider futuresProvider = new UpstoxFuturesProvider(instrumentResolver);
        OptionsProvider optionsProvider = new UpstoxOptionsProvider(
                new UpstoxOptionChainRestClient(jsonClient),
                instrumentResolver
        );
        NewsProvider newsProvider = new UpstoxNewsProvider(
                new UpstoxNewsRestClient(jsonClient)
        );

        brokerConnection = new UpstoxBrokerConnection(
                marketData,
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
                webSocketMultiplexer,
                futuresProvider,
                optionsProvider,
                newsProvider,
                new com.tradej.broker.api.port.ConditionalAlertProvider() {
                    @Override public String placeAlert(com.tradej.core.domain.model.ConditionalAlertRequest request) { throw new UnsupportedOperationException(); }
                    @Override public com.tradej.core.domain.model.ConditionalAlert getAlert(String alertId) { throw new UnsupportedOperationException(); }
                    @Override public java.util.List<com.tradej.core.domain.model.ConditionalAlert> listAlerts() { throw new UnsupportedOperationException(); }
                    @Override public boolean deleteAlert(String alertId) { throw new UnsupportedOperationException(); }
                }, // conditionalAlertProvider - not needed for market feed test
                null, // sliceOrderCommand - not needed for market feed test
                null, // dataServicesProvider - not needed for market feed test
                null, // profileProvider - not needed for market feed test
                instrumentLoader
        );

        CountDownLatch tickLatch = new CountDownLatch(1);
        brokerConnection.websocket().onMarketData(event -> {
            System.out.println("[TEST-DEBUG] Received WS Event: " + event.getClass().getSimpleName() + " -> " + event);
            if (event instanceof MarketTickEvent) {
                tickLatch.countDown();
            }
        });

        System.out.println("[TEST-DEBUG] Connecting to Upstox WebSocket...");
        brokerConnection.connect();
        assertTrue(brokerConnection.websocket().isConnected(), "Expected Upstox websocket to be connected");
        System.out.println("[TEST-DEBUG] Connected successfully.");

        MarketSubscriptionRequest subscriptionRequest = new MarketSubscriptionRequest("SBIN", ExchangeSegment.NSE_EQ);
        System.out.println("[TEST-DEBUG] Subscribing to: " + subscriptionRequest);
        brokerConnection.websocket().subscribe(
                List.of(subscriptionRequest),
                FeedMode.TICKER
        );

        assertTrue(brokerConnection.websocket().subscriptions().containsKey(subscriptionRequest),
                "Expected subscription to be registered");
        System.out.println("[TEST-DEBUG] Subscription registered in multiplexer.");

        boolean marketOpen = isExchangeSessionOpen(Exchange.NSE);
        System.out.println("[TEST-DEBUG] Indian Market Open status: " + marketOpen);

        if (marketOpen) {
            System.out.println("[TEST-DEBUG] Awaiting tick for 30 seconds...");
            boolean success = tickLatch.await(30, TimeUnit.SECONDS);
            assertTrue(success, "Expected at least one live tick from Upstox market feed during market hours");
        } else {
            System.out.println("[TEST-DEBUG] Market closed. Skipping tick arrival assertion.");
        }
    }

    private boolean isExchangeSessionOpen(Exchange exchange) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        if (exchange == Exchange.MCX) {
            return !time.isBefore(LocalTime.of(9, 0)) && !time.isAfter(LocalTime.of(23, 30));
        }
        return !time.isBefore(LocalTime.of(9, 15)) && !time.isAfter(LocalTime.of(15, 30));
    }
}
