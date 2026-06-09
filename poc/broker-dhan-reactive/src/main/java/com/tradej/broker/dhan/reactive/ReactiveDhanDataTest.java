package com.tradej.broker.dhan.reactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.reactive.adapter.DhanReactiveFuturesProvider;
import com.tradej.broker.dhan.reactive.adapter.DhanReactiveMarketDataProvider;
import com.tradej.broker.dhan.reactive.adapter.DhanReactiveOptionsProvider;
import com.tradej.broker.dhan.reactive.adapter.DhanReactivePortfolioProvider;
import com.tradej.broker.dhan.reactive.auth.DhanReactiveTokenManager;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentResolver;
import com.tradej.broker.dhan.reactive.resilience.DhanRateLimits;
import com.tradej.broker.dhan.reactive.resilience.MultiBucketRateLimiter;
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
    private static DhanReactiveOptionsProvider optionsProvider;
    private static DhanReactiveFuturesProvider futuresProvider;
    
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
            
            // Options tests
            testOptionExpiries().block();
            testOptionChain().block();
            
            // Futures test
            testFuturesHistory().block();
            
            // MCX Commodities test
            testMcxFutures().block();
            
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
        // Load credentials from config file (live/production)
        DhanCredentials credentials = loadLiveCredentials();
        
        System.out.println("📡 Connecting to Dhan LIVE/Production API...");
        System.out.println("   Client ID: " + credentials.clientId());
        System.out.println("   Source: config/dhan-local.properties");
        System.out.println();
        
        // Create settings (LIVE mode - not sandbox)
        DhanReactiveConnectionSettings settings = new DhanReactiveConnectionSettings(
            credentials.clientId(),
            credentials.accessToken(),
            false, // LIVE (not sandbox)
            10,
            java.time.Duration.ofSeconds(10),
            java.time.Duration.ofSeconds(15)
        );
        
        // Create WebClient
        WebClient webClient = WebClient.builder()
            .baseUrl(settings.baseUrl())
            .build();
        
        // Create HTTP client with token provider
        DhanReactiveTokenManager tokenManager = new DhanReactiveTokenManager(
            createSimpleHttpClient(webClient, settings),
            settings
        );
        
        DhanReactiveHttpClient httpClient = new DhanReactiveHttpClient(
            webClient,
            tokenManager,
            settings,
            DhanRateLimits.createDefault()
        );
        
        // Create instrument resolver
        DhanInstrumentResolver resolver = createSimpleResolver();
        
        // Create providers
        DhanReactiveMarketDataProvider marketDataProvider = new DhanReactiveMarketDataProvider(
            httpClient,
            resolver
        );
        DhanReactivePortfolioProvider portfolioProvider = new DhanReactivePortfolioProvider(httpClient);
        
        // Create options provider
        optionsProvider = new DhanReactiveOptionsProvider(httpClient, resolver);
        
        // Create futures provider
        futuresProvider = new DhanReactiveFuturesProvider(httpClient, resolver);
        
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
        // Create rate limiter
        MultiBucketRateLimiter rateLimiter = DhanRateLimits.createDefault();
        
        // Temporary client just for token refresh (before main client is created)
        return new DhanReactiveHttpClient(
            webClient,
            () -> Mono.empty(), // No token provider yet
            settings,
            rateLimiter
        );
    }
    
    private static DhanInstrumentResolver createSimpleResolver() {
        return new DhanInstrumentResolver() {
            @Override
            public DhanInstrumentDefinition resolve(InstrumentKey key) {
                return switch (key.symbol()) {
                    // Index
                    case "NIFTY" -> new DhanInstrumentDefinition("IDX_I", "13", "NIFTY");
                    case "BANKNIFTY" -> new DhanInstrumentDefinition("IDX_I", "14", "BANKNIFTY");
                    
                    // Equity
                    case "RELIANCE" -> new DhanInstrumentDefinition("NSE_EQ", "2885", "RELIANCE");
                    case "TCS" -> new DhanInstrumentDefinition("NSE_EQ", "3647", "TCS");
                    
                    // MCX Commodities
                    case "GOLD" -> new DhanInstrumentDefinition("MCX", "100", "GOLD");
                    case "SILVER" -> new DhanInstrumentDefinition("MCX", "101", "SILVER");
                    case "CRUDEOIL" -> new DhanInstrumentDefinition("MCX", "102", "CRUDEOIL");
                    case "NATURALGAS" -> new DhanInstrumentDefinition("MCX", "103", "NATURALGAS");
                    
                    default -> {
                        // Default to MCX for commodity-like symbols
                        String sym = key.symbol().toUpperCase();
                        if (sym.contains("GOLD") || sym.contains("SILVER") || 
                            sym.contains("CRUDE") || sym.contains("GAS") || sym.contains("COPPER")) {
                            yield new DhanInstrumentDefinition("MCX", "100", sym);
                        }
                        yield new DhanInstrumentDefinition("IDX_I", "13", sym);
                    }
                };
            }
        };
    }
    
    /**
     * Load credentials from config/dhan-local.properties (LIVE/PRODUCTION)
     */
    private static DhanCredentials loadLiveCredentials() {
        Path configFile = Path.of("config/dhan-local.properties");
        
        if (!Files.exists(configFile)) {
            throw new IllegalStateException(
                "Config file not found: " + configFile.toAbsolutePath() + "\n" +
                "Please ensure config/dhan-local.properties exists with:\n" +
                "  dhan.clientId=your_client_id\n" +
                "  dhan.accessToken=your_access_token"
            );
        }
        
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(configFile.toFile())) {
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config file: " + configFile, e);
        }
        
        String clientId = props.getProperty("dhan.clientId");
        String accessToken = props.getProperty("dhan.accessToken");
        
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Missing dhan.clientId in config file");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Missing dhan.accessToken in config file");
        }
        
        return new DhanCredentials(clientId, accessToken);
    }
    
    private record DhanCredentials(String clientId, String accessToken) {}
    
    // Test 1: Get LTP
    private static Mono<Void> testLtp() {
        System.out.println("🔵 Test 1: Fetch REAL LTP for NIFTY (Live Market)");
        
        InstrumentKey key = new InstrumentKey("NIFTY", ExchangeSegment.IDX_I);
        
        return broker.getLtpPaisa(key)
            .doOnNext(ltp -> {
                System.out.println("   ✅ NIFTY LTP: " + (ltp / 100.0));
                assert ltp > 0 : "LTP should be positive";
                assert ltp > 1000000 : "NIFTY should be > 10,000"; // NIFTY is around 24,000+
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
        System.out.println("🔵 Test 6: Fetch REAL Historical Candles for NIFTY (Daily, last 30 days)");
        
        InstrumentKey key = new InstrumentKey("NIFTY", ExchangeSegment.IDX_I);
        CandleHistoryRequest request = new CandleHistoryRequest(
            key,
            "1d",  // Daily candles
            LocalDate.now().minusDays(30),
            LocalDate.now()
        );
        
        return broker.getCandles(request)
            .take(10)
            .collectList()
            .doOnNext(candles -> {
                System.out.println("   ✅ REAL Historical Candles Received: " + candles.size());
                if (!candles.isEmpty()) {
                    System.out.println("   Sample Candles (NIFTY Daily):");
                    candles.stream().limit(3).forEach(c -> 
                        System.out.println("      Date: " + java.time.Instant.ofEpochMilli(c.startTimeMs()) +
                                         ", O: " + c.openPaisa() + 
                                         ", H: " + c.highPaisa() + 
                                         ", L: " + c.lowPaisa() + 
                                         ", C: " + c.closePaisa())
                    );
                }
                assert !candles.isEmpty() : "Should receive historical candles";
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 7: Get Batch LTP
    private static Mono<Void> testBatchLtp() {
        System.out.println("🔵 Test 7: Fetch Batch LTP (NIFTY, BANKNIFTY) - Live Market");
        
        List<InstrumentKey> symbols = List.of(
            new InstrumentKey("NIFTY", ExchangeSegment.IDX_I),
            new InstrumentKey("BANKNIFTY", ExchangeSegment.IDX_I)
        );
        
        return broker.getLtpBatch(symbols)
            .doOnNext(ltpMap -> {
                System.out.println("   ✅ REAL Batch LTP:");
                ltpMap.forEach((key, ltp) -> 
                    System.out.println("      " + key.symbol() + ": " + (ltp / 100.0))
                );
                assert ltpMap.size() == 2 : "Should have 2 symbols";
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 8: Get Option Expiries
    private static Mono<Void> testOptionExpiries() {
        System.out.println("🔵 Test 8: Fetch REAL Option Expiries for NIFTY");
        
        InstrumentKey key = new InstrumentKey("NIFTY", ExchangeSegment.IDX_I);
        
        return optionsProvider.getExpiries(key)
            .take(5)
            .collectList()
            .doOnNext(expiries -> {
                System.out.println("   ✅ NIFTY Option Expiries (next 5):");
                expiries.forEach(expiry -> 
                    System.out.println("      - " + expiry)
                );
                assert !expiries.isEmpty() : "Should have option expiries";
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 9: Get Option Chain
    private static Mono<Void> testOptionChain() {
        System.out.println("🔵 Test 9: Fetch REAL Option Chain for NIFTY (nearest expiry)");
        
        InstrumentKey key = new InstrumentKey("NIFTY", ExchangeSegment.IDX_I);
        
        // First get expiries, then fetch chain for first expiry
        return optionsProvider.getExpiries(key)
            .next()
            .flatMap(nearestExpiry -> {
                System.out.println("   Fetching option chain for expiry: " + nearestExpiry);
                
                return optionsProvider.getOptionChain(key, nearestExpiry)
                    .take(10)
                    .collectList()
                    .doOnNext(strikes -> {
                        System.out.println("   ✅ NIFTY Option Chain (first 10 strikes):");
                        strikes.stream().limit(5).forEach(strike -> {
                            System.out.println("      Strike: " + (strike.strikePricePaisa() / 100.0));
                            if (strike.call() != null) {
                                System.out.println("        CE: LTP=" + strike.call().ltp() + 
                                                 ", OI=" + strike.call().openInterest());
                            }
                            if (strike.put() != null) {
                                System.out.println("        PE: LTP=" + strike.put().ltp() + 
                                                 ", OI=" + strike.put().openInterest());
                            }
                        });
                        assert !strikes.isEmpty() : "Should have option strikes";
                    });
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 10: Get Futures History
    private static Mono<Void> testFuturesHistory() {
        System.out.println("🔵 Test 10: Fetch REAL NIFTY Futures History (last 30 days)");
        
        InstrumentKey key = new InstrumentKey("NIFTY", ExchangeSegment.IDX_I);
        
        return futuresProvider.getFuturesHistory(
                key,
                LocalDate.now().minusDays(30),
                LocalDate.now()
            )
            .take(10)
            .collectList()
            .doOnNext(bars -> {
                System.out.println("   ✅ NIFTY Futures Historical Bars (last 10):");
                bars.stream().limit(5).forEach(bar -> {
                    System.out.println("      Date: " + bar.timestamp().substring(0, 10));
                    System.out.println("        O: " + bar.openPaisa() + 
                                     ", H: " + bar.highPaisa() + 
                                     ", L: " + bar.lowPaisa() + 
                                     ", C: " + bar.closePaisa());
                    System.out.println("        Volume: " + bar.volume() + ", OI: " + bar.openInterest());
                });
                assert !bars.isEmpty() : "Should have futures bars";
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
    
    // Test 11: Get MCX Commodity Futures
    private static Mono<Void> testMcxFutures() {
        System.out.println("🔵 Test 11: Fetch REAL GOLD MCX Futures History (last 30 days)");
        
        InstrumentKey key = new InstrumentKey("GOLD", ExchangeSegment.MCX_COMM);
        
        return futuresProvider.getFuturesHistory(
                key,
                LocalDate.now().minusDays(30),
                LocalDate.now()
            )
            .take(10)
            .collectList()
            .doOnNext(bars -> {
                System.out.println("   ✅ GOLD MCX Futures Historical Bars (last 10):");
                bars.stream().limit(5).forEach(bar -> {
                    System.out.println("      Date: " + bar.timestamp().substring(0, 10));
                    System.out.println("        O: " + bar.openPaisa() + 
                                     ", H: " + bar.highPaisa() + 
                                     ", L: " + bar.lowPaisa() + 
                                     ", C: " + bar.closePaisa());
                    System.out.println("        Volume: " + bar.volume() + ", OI: " + bar.openInterest() + ", Spot: " + bar.spotPrice());
                });
                assert !bars.isEmpty() : "Should have MCX futures bars";
            })
            .doOnSuccess(v -> System.out.println())
            .then();
    }
}
