package com.tradej.broker.dhan.reactive;

import com.tradej.broker.dhan.reactive.auth.DhanReactiveTokenManager;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.broker.dhan.reactive.websocket.DhanReactiveWebSocketClient;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.broker.dhan.reactive.resilience.DhanRateLimits;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

/**
 * Simple test to verify live WebSocket market feed from Dhan broker.
 * This CONSUMES the broker's WebSocket (doesn't host one).
 * 
 * Tests all subscription modes for MCX commodities:
 * - LTP (Last Traded Price) - Fast, lightweight
 * - QUOTE (Full OHLCV) - Complete market data
 * - DEPTH (Market Depth) - Order book (5 levels)
 * 
 * Note: MCX market is LIVE, NSE index market is CLOSED
 */
public class WebSocketMarketFeedTest {
    
    public static void main(String[] args) {
        System.out.println("🌐 Testing Live WebSocket Market Feed from Dhan");
        System.out.println("=".repeat(60));
        System.out.println();
        
        try {
            // Load credentials
            DhanCredentials credentials = loadCredentials();
            
            System.out.println("📡 Connecting to Dhan WebSocket (LIVE mode - MCX only)...");
            System.out.println("   Client ID: " + credentials.clientId());
            System.out.println("   WebSocket URL: wss://api.dhan.co/v2/feed");
            System.out.println("   Market Status: MCX LIVE, NSE CLOSED");
            System.out.println();
            
            // Create settings
            DhanReactiveConnectionSettings settings = new DhanReactiveConnectionSettings(
                credentials.clientId(),
                credentials.accessToken(),
                false, // LIVE (not sandbox)
                10,
                Duration.ofSeconds(10),
                Duration.ofSeconds(15)
            );
            
            // Create WebClient
            WebClient webClient = WebClient.builder()
                .baseUrl(settings.baseUrl())
                .build();
            
            // Create HTTP client (with dummy token provider initially)
            DhanReactiveHttpClient httpClient = new DhanReactiveHttpClient(
                webClient,
                () -> reactor.core.publisher.Mono.just("dummy"),
                settings,
                DhanRateLimits.createDefault()
            );
            
            // Create token manager
            DhanReactiveTokenManager tokenManager = new DhanReactiveTokenManager(httpClient, settings);
            
            // Re-create HTTP client with proper token manager
            httpClient = new DhanReactiveHttpClient(
                webClient,
                tokenManager,
                settings,
                DhanRateLimits.createDefault()
            );
            DhanReactiveWebSocketClient wsClient = new DhanReactiveWebSocketClient(settings, tokenManager);
            
            // Test all 4 subscription modes for MCX commodities
            testGoldLtpFeed(wsClient);        // Mode 1: LTP (fast, lightweight)
            testGoldQuoteFeed(wsClient);     // Mode 2: Full Quote (OHLCV)
            testGoldDepthFeed(wsClient);     // Mode 3: Level 5 Depth (Order Book)
            testMultipleCommoditiesFeed(wsClient);  // Mode 4: Multi-symbol
            
            // Wait for updates
            System.out.println("⏳ Waiting for live market data (30 seconds)...");
            Thread.sleep(30000);
            
            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("✅ WebSocket feed test complete!");
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("=".repeat(60));
            System.err.println("❌ Test failed: " + e.getMessage());
            System.err.println("=".repeat(60));
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    // Mode 1: LTP (Last Traded Price) - Fast, lightweight
    private static void testGoldLtpFeed(DhanReactiveWebSocketClient wsClient) {
        System.out.println("🔵 Test 1: GOLD MCX - LTP Mode (Last Traded Price)");
        System.out.println("   Mode: Lightweight, price updates only");
        
        InstrumentKey gold = new InstrumentKey("GOLD", ExchangeSegment.MCX_COMM);
        
        Flux<DhanReactiveWebSocketClient.MarketDataUpdate> liveStream = 
            wsClient.subscribeToLtp(List.of(gold));
        
        liveStream
            .take(Duration.ofSeconds(10))
            .subscribe(
                update -> {
                    System.out.println("   🥇 GOLD LTP: ₹" + update.ltp() + 
                                     " | Time: " + update.timestamp());
                },
                error -> System.err.println("   ❌ Error: " + error.getMessage()),
                () -> System.out.println("   ✅ GOLD LTP stream complete\n")
            );
    }
    
    // Mode 2: Full Quote (OHLCV) - Complete market data
    private static void testGoldQuoteFeed(DhanReactiveWebSocketClient wsClient) {
        System.out.println("🔵 Test 2: GOLD MCX - QUOTE Mode (Full OHLCV)");
        System.out.println("   Mode: Complete market data (price, volume, OHLC)");
        
        InstrumentKey gold = new InstrumentKey("GOLD", ExchangeSegment.MCX_COMM);
        
        Flux<DhanReactiveWebSocketClient.MarketDataUpdate> liveStream = 
            wsClient.subscribeToQuote(List.of(gold));
        
        liveStream
            .take(Duration.ofSeconds(10))
            .subscribe(
                update -> {
                    System.out.println("   🥇 GOLD Quote: ₹" + update.ltp() + 
                                     " | Volume: " + update.volume() +
                                     " | O: " + update.open() + 
                                     " | H: " + update.high() + 
                                     " | L: " + update.low() +
                                     " | Time: " + update.timestamp());
                },
                error -> System.err.println("   ❌ Error: " + error.getMessage()),
                () -> System.out.println("   ✅ GOLD QUOTE stream complete\n")
            );
    }
    
    // Mode 3: Level 5 Depth (Order Book) - Full market depth
    private static void testGoldDepthFeed(DhanReactiveWebSocketClient wsClient) {
        System.out.println("🔵 Test 3: GOLD MCX - DEPTH Mode (Level 5 Order Book)");
        System.out.println("   Mode: Full Level 5 depth (5 bid + 5 ask levels)");
        
        InstrumentKey gold = new InstrumentKey("GOLD", ExchangeSegment.MCX_COMM);
        
        Flux<DhanReactiveWebSocketClient.MarketDataUpdate> liveStream = 
            wsClient.subscribeToDepth(List.of(gold));
        
        liveStream
            .take(Duration.ofSeconds(10))
            .subscribe(
                update -> {
                    System.out.println("   🥇 GOLD Depth: ₹" + update.ltp() + 
                                     " | Volume: " + update.volume());
                    
                    // Show Level 5 depth data
                    if (update.hasDepthData()) {
                        System.out.println(update.formatDepth());
                    } else {
                        System.out.println("   ⏳ Waiting for depth data...");
                    }
                },
                error -> System.err.println("   ❌ Error: " + error.getMessage()),
                () -> System.out.println("   ✅ GOLD DEPTH stream complete\n")
            );
    }
    
    // Mode 3: Multiple Commodities - Multi-symbol streaming
    private static void testMultipleCommoditiesFeed(DhanReactiveWebSocketClient wsClient) {
        System.out.println("🔵 Test 3: Multiple MCX Commodities - LTP Mode");
        System.out.println("   Mode: Multi-symbol streaming (GOLD, SILVER, CRUDEOIL)");
        
        List<InstrumentKey> commodities = List.of(
            new InstrumentKey("GOLD", ExchangeSegment.MCX_COMM),
            new InstrumentKey("SILVER", ExchangeSegment.MCX_COMM),
            new InstrumentKey("CRUDEOIL", ExchangeSegment.MCX_COMM)
        );
        
        Flux<DhanReactiveWebSocketClient.MarketDataUpdate> liveStream = 
            wsClient.subscribeToLtp(commodities);
        
        liveStream
            .take(Duration.ofSeconds(15))
            .subscribe(
                update -> {
                    String symbol = update.symbol();
                    String emoji = switch (symbol) {
                        case "GOLD" -> "🥇";
                        case "SILVER" -> "🥈";
                        case "CRUDEOIL" -> "🛢️";
                        default -> "📊";
                    };
                    
                    System.out.println("   " + emoji + " " + symbol + ": ₹" + update.ltp() + 
                                     " | Volume: " + update.volume() +
                                     " | Time: " + update.timestamp());
                },
                error -> System.err.println("   ❌ Error: " + error.getMessage()),
                () -> System.out.println("   ✅ Multi-commodity stream complete\n")
            );
    }
    
    private static DhanCredentials loadCredentials() {
        Path configFile = Path.of("config/dhan-local.properties");
        
        if (!Files.exists(configFile)) {
            throw new IllegalStateException(
                "Config file not found: " + configFile.toAbsolutePath()
            );
        }
        
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(configFile.toFile())) {
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
        
        String clientId = props.getProperty("dhan.clientId");
        String accessToken = props.getProperty("dhan.accessToken");
        
        if (clientId == null || accessToken == null) {
            throw new IllegalStateException("Missing credentials in config file");
        }
        
        return new DhanCredentials(clientId, accessToken);
    }
    
    private record DhanCredentials(String clientId, String accessToken) {}
}
