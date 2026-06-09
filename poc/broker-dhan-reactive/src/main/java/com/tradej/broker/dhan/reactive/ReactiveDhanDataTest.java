package com.tradej.broker.dhan.reactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.reactive.adapter.DhanReactiveMarketDataProvider;
import com.tradej.broker.dhan.reactive.adapter.DhanReactivePortfolioProvider;
import com.tradej.broker.dhan.reactive.auth.DhanReactiveTokenManager;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentResolver;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.FundLimits;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Simple standalone test to verify all reactive Dhan data endpoints.
 * 
 * HOW TO RUN:
 * 1. Set environment variables:
 *    export DHAN_CLIENT_ID="your_client_id"
 *    export DHAN_API_SECRET="your_api_secret"
 * 
 * 2. Compile and run:
 *    cd /Users/apple/Downloads/Trade_J
 *    ./gradlew :broker-dhan-reactive:run -DmainClass=com.tradej.broker.dhan.reactive.ReactiveDhanDataTest
 * 
 * OR run from IDE:
 *    Right-click -> Run 'ReactiveDhanDataTest.main()'
 */
public class ReactiveDhanDataTest {
    
    private static DhanReactiveBroker broker;
    
    public static void main(String[] args) {
        System.out.println("🚀 Reactive Dhan Broker - Data Endpoints Test");
        System.out.println("=".repeat(60));
        System.out.println();
        
        try {
            // Initialize broker
            initBroker();
            
            // Run all tests
            testLtp().block();
            testQuote().block();
            testFundLimits().block();
            testHoldings().block();
            testPositions().block();
            testHistoricalCandles().block();
            testBatchLtp().block();
            
            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("✅ ALL TESTS PASSED!");
            System.out.println("=".repeat(60));
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("=".repeat(60));
            System.err.println("❌ TEST FAILED: " + e.getMessage());
            System.err.println("=".repeat(60));
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void initBroker() {
        // Load credentials from config file (same as original broker-dhan)
        DhanCredentials credentials = loadCredentials();
        
        System.out.println("📡 Connecting to Dhan Sandbox API...");
        System.out.println("   Client ID: " + credentials.clientId());
        System.out.println("   Source: config/dhan-sandbox.properties");
        System.out.println();
        
        // Create settings (sandbox mode)
        DhanReactiveConnectionSettings settings = new DhanReactiveConnectionSettings(
            credentials.clientId(),
            credentials.accessToken(),
            true, // sandbox
            10,
            java.time.Duration.ofSeconds(10),
            java.time.Duration.ofSeconds(15)
        );
        
        // Create WebClient
        WebClient webClient = WebClient.builder()
            .baseUrl(settings.restBaseUrl())
            .build();
        
        // Create HTTP client with token provider
        DhanReactiveTokenManager tokenManager = new DhanReactiveTokenManager(
            createSimpleHttpClient(webClient, settings),
            settings
        );
        
        DhanReactiveHttpClient httpClient = new DhanReactiveHttpClient(
            webClient,
            tokenManager,
            settings
        );
        
        // Create instrument resolver
        DhanInstrumentResolver resolver = createSimpleResolver();
        
        // Create providers
        DhanReactiveMarketDataProvider marketDataProvider = new DhanReactiveMarketDataProvider(
            httpClient,
            resolver
        );
        DhanReactivePortfolioProvider portfolioProvider = new DhanReactivePortfolioProvider(httpClient);
        
        // Create broker facade
        broker = new DhanReactiveBroker(
            marketDataProvider,
            null, // Order provider - not tested here
            portfolioProvider,
            null  // WebSocket client - not tested here
        );
        
        System.out.println("✅ Broker initialized successfully");
        System.out.println();
    }
    
    private static DhanReactiveHttpClient createSimpleHttpClient(
            WebClient webClient, 
            DhanReactiveConnectionSettings settings
    ) {
        // Temporary client just for token refresh (before main client is created)
        return new DhanReactiveHttpClient(
            webClient,
            () -> Mono.empty(), // No token provider yet
            settings
        );
    }
    
    private static DhanInstrumentResolver createSimpleResolver() {
        return new DhanInstrumentResolver() {
            @Override
            public DhanInstrumentDefinition resolve(InstrumentKey key) {
                return switch (key.symbol()) {
                    case "RELIANCE" -> new DhanInstrumentDefinition("NSE_EQ", "2885", "RELIANCE", null, null, null, 0L);
                    case "TCS" -> new DhanInstrumentDefinition("NSE_EQ", "3647", "TCS", null, null, null, 0L);
                    case "NIFTY" -> new DhanInstrumentDefinition("NSE_FNO", "13013", "NIFTY", null, null, null, 0L);
                    case "BANKNIFTY" -> new DhanInstrumentDefinition("NSE_FNO", "13014", "BANKNIFTY", null, null, null, 0L);
                    default -> new DhanInstrumentDefinition("NSE_EQ", "2885", key.symbol(), null, null, null, 0L);
                };
            }
        };
    }
    
    /**
     * Load credentials from config/dhan-sandbox.properties
     * Same approach as original broker-dhan module
     */
    private static DhanCredentials loadCredentials() {
        Path configFile = Path.of("config/dhan-sandbox.properties");
        
        if (!Files.exists(configFile)) {
            throw new IllegalStateException(
                "Config file not found: " + configFile.toAbsolutePath() + "\n" +
                "Please ensure config/dhan-sandbox.properties exists with:\n" +
                "  dhan.sandbox.clientId=your_client_id\n" +
                "  dhan.sandbox.accessToken=your_access_token"
            );
        }
        
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(configFile.toFile())) {
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config file: " + configFile, e);
        }
        
        String clientId = props.getProperty("dhan.sandbox.clientId");
        String accessToken = props.getProperty("dhan.sandbox.accessToken");
        
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Missing dhan.sandbox.clientId in config file");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Missing dhan.sandbox.accessToken in config file");
        }
        
        return new DhanCredentials(clientId, accessToken);
    }
    
    private record DhanCredentials(String clientId, String accessToken) {}
    
    // Test 1: Get LTP
    private static Mono<Void> testLtp() {
        System.out.println("🔵 Test 1: Fetch LTP for RELIANCE");
        
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        
        return broker.getLtpPaisa(key)
            .doOnNext(ltp -> {
                System.out.println("   ✅ LTP: ₹" + (ltp / 100.0));
                assert ltp > 0 : "LTP should be positive";
                assert ltp < 1_000_000 : "LTP should be reasonable";
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 2: Get Quote
    private static Mono<Void> testQuote() {
        System.out.println("🔵 Test 2: Fetch Quote for RELIANCE");
        
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        
        return broker.getQuote(key)
            .doOnNext(quote -> {
                System.out.println("   ✅ Quote Data:");
                System.out.println("      LTP: ₹" + (quote.ltpPaisa() / 100.0));
                System.out.println("      Open: ₹" + (quote.openPaisa() / 100.0));
                System.out.println("      High: ₹" + (quote.highPaisa() / 100.0));
                System.out.println("      Low: ₹" + (quote.lowPaisa() / 100.0));
                System.out.println("      Volume: " + quote.volume());
                
                assert quote.ltpPaisa() > 0 : "LTP should be positive";
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 3: Get Fund Limits
    private static Mono<Void> testFundLimits() {
        System.out.println("🔵 Test 3: Fetch Fund Limits");
        
        return broker.getFundLimits()
            .doOnNext(limits -> {
                System.out.println("   ✅ Fund Limits:");
                System.out.println("      Available: ₹" + limits.availableBalance());
                System.out.println("      Utilized: ₹" + limits.utilizedMargin());
                System.out.println("      Total: ₹" + limits.totalLimit());
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 4: Get Holdings
    private static Mono<Void> testHoldings() {
        System.out.println("🔵 Test 4: Fetch Holdings");
        
        return broker.getHoldings()
            .collectList()
            .doOnNext(holdings -> {
                System.out.println("   ✅ Holdings Count: " + holdings.size());
                if (!holdings.isEmpty()) {
                    System.out.println("   Sample Holdings:");
                    holdings.stream().limit(3).forEach(h -> 
                        System.out.println("      - " + h.symbol() + ": " + h.totalQuantity() + " shares")
                    );
                }
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 5: Get Positions
    private static Mono<Void> testPositions() {
        System.out.println("🔵 Test 5: Fetch Positions");
        
        return broker.getPositions()
            .collectList()
            .doOnNext(positions -> {
                System.out.println("   ✅ Positions Count: " + positions.size());
                if (!positions.isEmpty()) {
                    System.out.println("   Sample Positions:");
                    positions.stream().limit(3).forEach(p -> 
                        System.out.println("      - " + p.symbol() + ": " + p.quantity() + " qty")
                    );
                }
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 6: Get Historical Candles
    private static Mono<Void> testHistoricalCandles() {
        System.out.println("🔵 Test 6: Fetch Historical Candles (5-min, last 1 day)");
        
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        CandleHistoryRequest request = new CandleHistoryRequest(
            key,
            "5m",
            LocalDate.now().minusDays(1),
            LocalDate.now()
        );
        
        return broker.getCandles(request)
            .take(5)
            .collectList()
            .doOnNext(candles -> {
                System.out.println("   ✅ Candles Received: " + candles.size());
                if (!candles.isEmpty()) {
                    System.out.println("   Latest Candle:");
                    Candle latest = candles.get(candles.size() - 1);
                    System.out.println("      O: " + latest.openPaisa() + 
                                     ", H: " + latest.highPaisa() + 
                                     ", L: " + latest.lowPaisa() + 
                                     ", C: " + latest.closePaisa());
                }
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 7: Get Batch LTP
    private static Mono<Void> testBatchLtp() {
        System.out.println("🔵 Test 7: Fetch Batch LTP (RELIANCE, TCS)");
        
        List<InstrumentKey> symbols = List.of(
            new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ),
            new InstrumentKey("TCS", ExchangeSegment.NSE_EQ)
        );
        
        return broker.getLtpBatch(symbols)
            .doOnNext(ltpMap -> {
                System.out.println("   ✅ Batch LTP:");
                ltpMap.forEach((key, ltp) -> 
                    System.out.println("      " + key.symbol() + ": ₹" + (ltp / 100.0))
                );
                assert ltpMap.size() == 2 : "Should have 2 symbols";
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
}
