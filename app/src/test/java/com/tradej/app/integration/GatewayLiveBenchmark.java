package com.tradej.app.integration;

import com.tradej.app.TradingApplication;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Tag("integration")
public class GatewayLiveBenchmark {

    private static final Logger log = LoggerFactory.getLogger(GatewayLiveBenchmark.class);

    private static final List<String> SYMBOLS = Arrays.asList(
            "RELIANCE", "TCS", "HDFCBANK", "ICICIBANK", "INFY", "ITC", "SBIN", "BHARTIARTL", "BAJFINANCE", "LARSEN",
            "HINDUNILVR", "AXISBANK", "KOTAKBANK", "LT", "ASIANPAINT", "MARUTI", "HCLTECH", "SUNPHARMA", "TITAN", "ULTRACEMCO",
            "WIPRO", "BAJAJFINSV", "TATASTEEL", "NTPC", "POWERGRID", "INDUSINDBK", "NESTLEIND", "ONGC", "TECHM", "GRASIM",
            "M&M", "HINDALCO", "JSWSTEEL", "ADANIPORTS", "ADANIENT", "COALINDIA", "TATACOMM", "TATAMOTORS", "HEROMOTOCO", "UPL",
            "BPCL", "DRREDDY", "DIVISLAB", "BRITANNIA", "CIPLA", "APOLLOHOSP", "EICHERMOT", "BAJAJ-AUTO", "SHREECEM", "TATACONSUM",
            "PIDILITIND", "DABUR", "GODREJCP", "HAVELLS", "ICICIPRULI", "SRF", "MCDOWELL-N", "INDIGO", "NAUKRI", "MARICO",
            "ICICIGI", "AUROPHARMA", "BERGEPAINT", "LICI", "AMBUJACEM", "COLPAL", "MUTHOOTFIN", "BOSCHLTD", "TORNTPHARM", "PGHH",
            "BIOCON", "BANKBARODA", "CHOLAFIN", "GAIL", "SIEMENS", "MOTHERSUMI", "PNB", "TVSMOTOR", "HINDPETRO", "UBL",
            "YESBANK", "LUPIN", "BANDHANBNK", "DLF", "PEL", "MRF", "IGL", "ACC", "NMDC", "PETRONET",
            "GMRINFRA", "IDEA", "PFC", "RECLTD", "VEDL", "ZENTEC", "BHEL", "BEL", "HAL", "IRCTC"
    );

    @org.junit.jupiter.api.Test
    public void testLiveBenchmark() throws Exception {
        System.setProperty("trade.broker-type", "gateway");
        System.setProperty("trade.broker.dhan.enabled", "true");
        System.setProperty("trade.broker.icici.enabled", "true");
        System.setProperty("trade.broker.upstox.enabled", "true");
        System.setProperty("spring.main.allow-bean-definition-overriding", "true");

        ApplicationContext context = new org.springframework.boot.builder.SpringApplicationBuilder(TradingApplication.class)
                .profiles("prod")
                .initializers(ctx -> {
                    ctx.addBeanFactoryPostProcessor(beanFactory -> {
                        if (beanFactory instanceof org.springframework.beans.factory.support.BeanDefinitionRegistry registry) {
                            java.util.Set<String> removedBeans = new java.util.HashSet<>();
                            for (String name : registry.getBeanDefinitionNames()) {
                                org.springframework.beans.factory.config.BeanDefinition bd = registry.getBeanDefinition(name);
                                String beanClassName = bd.getBeanClassName();
                                if (beanClassName != null && beanClassName.startsWith("com.tradej.app.integration")) {
                                    removedBeans.add(name);
                                }
                            }
                            for (String name : registry.getBeanDefinitionNames()) {
                                org.springframework.beans.factory.config.BeanDefinition bd = registry.getBeanDefinition(name);
                                String factoryBeanName = bd.getFactoryBeanName();
                                if (factoryBeanName != null && removedBeans.contains(factoryBeanName)) {
                                    removedBeans.add(name);
                                }
                            }
                            for (String name : removedBeans) {
                                registry.removeBeanDefinition(name);
                                log.info("Removed test bean definition: {}", name);
                            }
                        }
                    });
                })
                .properties(
                        "trade.broker-type=gateway",
                        "trade.broker.dhan.enabled=true",
                        "trade.broker.icici.enabled=true",
                        "trade.broker.upstox.enabled=true",
                        "trade.scan.enabled=true",
                        "spring.main.allow-bean-definition-overriding=true",
                        "spring.config.import=optional:file:/Users/apple/Downloads/Trade_J/config/dhan-local.properties,optional:file:/Users/apple/Downloads/Trade_J/config/icici-local.properties,optional:file:/Users/apple/Downloads/Trade_J/config/upstox-live.properties",
                        "trade.broker.client-id=${dhan.clientId:}",
                        "trade.broker.access-token=${dhan.accessToken:}",
                        "trade.broker.pin-file=/Users/apple/Downloads/Trade_J/config/dhan-pin.txt",
                        "trade.broker.totp-secret-file=/Users/apple/Downloads/Trade_J/config/dhan-totp-secret.txt",
                        "trade.broker.refresh-buffer-minutes=${dhan.refreshBufferMinutes:10}",
                        "trade.icici.appKey=${icici.appKey:}",
                        "trade.icici.secretKey=${icici.secretKey:}",
                        "trade.icici.authMode=${icici.authMode:BROWSER_AUTOMATED}",
                        "trade.icici.totpSecretFile=/Users/apple/Downloads/Trade_J/config/icici-totp-secret.txt",
                        "trade.icici.usernameFile=/Users/apple/Downloads/Trade_J/config/icici-username.txt",
                        "trade.icici.passwordFile=/Users/apple/Downloads/Trade_J/config/icici-password.txt",
                        "trade.icici.apiSessionFile=/Users/apple/Downloads/Trade_J/config/icici-api-session.txt",
                        "trade.icici.tokenStateFile=/Users/apple/Downloads/Trade_J/runtime/icici-token-state.json",
                        "trade.icici.ordersEnabled=${icici.ordersEnabled:false}",
                        "trade.icici.refreshBufferMinutes=${icici.refreshBufferMinutes:10}",
                        "trade.icici.loginRedirectPort=${icici.loginRedirectPort:9080}",
                        "trade.icici.loginRedirectPath=${icici.loginRedirectPath:/api}",
                        "trade.icici.browserHeadless=${icici.browserHeadless:true}",
                        "trade.icici.browserLoginTimeoutSeconds=${icici.browserLoginTimeoutSeconds:120}",
                        "trade.upstox.client-id=${upstox.live.clientId:}",
                        "trade.upstox.client-secret=${upstox.live.clientSecret:}",
                        "trade.upstox.redirect-uri=${upstox.live.redirectUri:}",
                        "trade.upstox.access-token=${upstox.live.accessToken:}",
                        "trade.upstox.analytics-token=${upstox.live.analyticsToken:}",
                        "trade.upstox.analytics-only=${upstox.live.analyticsOnly:false}",
                        "trade.upstox.sandbox=${upstox.live.sandbox:false}"
                )
                .run();
        com.tradej.broker.api.IBrokerConnection brokerConnection = context.getBean("loadBalancedBrokerGateway", com.tradej.broker.api.IBrokerConnection.class);
        MarketDataProvider marketDataProvider = brokerConnection.marketData();
        
        log.info("Starting live historical benchmark for {} symbols", SYMBOLS.size());

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(30);
        String interval = "5m";

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalCandles = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(SYMBOLS.size());

        for (String symbol : SYMBOLS) {
            executor.submit(() -> {
                try {
                    InstrumentKey key = new InstrumentKey(symbol, ExchangeSegment.NSE_EQ);
                    CandleHistoryRequest request = new CandleHistoryRequest(key, interval, fromDate, toDate);
                    
                    log.info("Fetching {} from {} to {}", symbol, fromDate, toDate);
                    List<Candle> candles = marketDataProvider.getCandles(request);
                    
                    if (candles != null && !candles.isEmpty()) {
                        successCount.incrementAndGet();
                        totalCandles.addAndGet(candles.size());
                        log.info("Success: {} - {} candles fetched", symbol, candles.size());
                    } else {
                        log.warn("Empty response for {}", symbol);
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch {}", symbol, e);
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("===============================================");
        log.info("BENCHMARK COMPLETED in {} ms", durationMs);
        log.info("Total Symbols Requested: {}", SYMBOLS.size());
        log.info("Successful Downloads: {}", successCount.get());
        log.info("Failed Downloads: {}", failureCount.get());
        log.info("Total Candles Fetched: {}", totalCandles.get());
        log.info("Throughput: {} symbols/sec", String.format("%.2f", (double) SYMBOLS.size() / (durationMs / 1000.0)));
        log.info("===============================================");
    }
}
