package com.tradej.broker.dhan.reactive.integration;

import com.tradej.broker.dhan.reactive.adapter.DhanReactiveMarketDataProvider;
import com.tradej.broker.dhan.reactive.adapter.DhanReactiveOrderProvider;
import com.tradej.broker.dhan.reactive.adapter.DhanReactivePortfolioProvider;
import com.tradej.broker.dhan.reactive.auth.DhanReactiveTokenManager;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentResolver;
import com.tradej.broker.dhan.reactive.DhanReactiveBroker;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.FundLimits;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Live integration tests for Reactive Dhan Broker.
 * 
 * These tests make REAL API calls to Dhan sandbox environment.
 * 
 * TO RUN:
 * 1. Set system property: -Ddhan.reactive.live=true
 * 2. Ensure Dhan sandbox credentials are configured in config/dhan-sandbox.properties
 * 3. Run: ./gradlew :broker-dhan-reactive:test -Ddhan.reactive.live=true --tests DhanReactiveLiveIntegrationTest
 * 
 * WARNING: These tests will make actual API calls and may create/cancel orders!
 */
@EnabledIfSystemProperty(named = "dhan.reactive.live", matches = "true")
class DhanReactiveLiveIntegrationTest {
    
    private static DhanReactiveBroker broker;
    private static DhanReactiveHttpClient httpClient;
    private static DhanReactiveTokenManager tokenManager;
    
    @BeforeAll
    static void setUp() {
        // Load from environment or config files
        String clientId = System.getenv("DHAN_CLIENT_ID");
        String apiSecret = System.getenv("DHAN_API_SECRET");
        
        if (clientId == null || apiSecret == null) {
            // Fallback: try to load from config (you may need to implement this)
            throw new IllegalStateException(
                "Dhan credentials not found. Set DHAN_CLIENT_ID and DHAN_API_SECRET environment variables."
            );
        }
        
        DhanReactiveConnectionSettings settings = new DhanReactiveConnectionSettings(
            clientId,
            apiSecret,
            true, // sandbox mode
            10,
            Duration.ofSeconds(10),
            Duration.ofSeconds(15)
        );
        
        // Create HTTP client
        httpClient = new DhanReactiveHttpClient(settings);
        
        // Create token manager
        tokenManager = new DhanReactiveTokenManager(httpClient, settings);
        
        // Wire token manager into HTTP client (via reflection or constructor)
        // Note: You'll need to update DhanReactiveHttpClient to accept token provider
        // For now, we'll test components individually
        
        // Create instrument resolver (simple implementation)
        DhanInstrumentResolver resolver = createSimpleResolver();
        
        // Create providers
        DhanReactiveMarketDataProvider marketDataProvider = new DhanReactiveMarketDataProvider(
            httpClient, resolver
        );
        DhanReactiveOrderProvider orderProvider = new DhanReactiveOrderProvider(httpClient);
        DhanReactivePortfolioProvider portfolioProvider = new DhanReactivePortfolioProvider(httpClient);
        
        broker = new DhanReactiveBroker(
            marketDataProvider,
            orderProvider,
            portfolioProvider,
            null // WebSocket client not tested in live mode
        );
    }
    
    private static DhanInstrumentResolver createSimpleResolver() {
        // Simple resolver that maps known symbols to security IDs
        return new DhanInstrumentResolver() {
            @Override
            public DhanInstrumentDefinition resolve(InstrumentKey key) {
                return switch (key.symbol()) {
                    case "RELIANCE" -> new DhanInstrumentDefinition("NSE_EQ", "2885", "RELIANCE");
                    case "TCS" -> new DhanInstrumentDefinition("NSE_EQ", "3647", "TCS");
                    case "NIFTY" -> new DhanInstrumentDefinition("NSE_FNO", "13013", "NIFTY");
                    case "BANKNIFTY" -> new DhanInstrumentDefinition("NSE_FNO", "13014", "BANKNIFTY");
                    default -> new DhanInstrumentDefinition("NSE_EQ", "2885", key.symbol());
                };
            }
        };
    }
    
    @Test
    @DisplayName("LIVE: Should fetch LTP for RELIANCE")
    void testGetLtp() {
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        
        System.out.println("🔵 Fetching LTP for RELIANCE...");
        
        StepVerifier.create(broker.getLtpPaisa(key))
            .assertNext(ltp -> {
                System.out.println("✅ RELIANCE LTP: ₹" + (ltp / 100.0));
                assert ltp > 0 : "LTP should be positive";
                assert ltp < 1_000_000 : "LTP should be reasonable (< ₹10,000)";
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("LIVE: Should fetch quote for RELIANCE")
    void testGetQuote() {
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        
        System.out.println("🔵 Fetching quote for RELIANCE...");
        
        StepVerifier.create(broker.getQuote(key))
            .assertNext(quote -> {
                System.out.println("✅ RELIANCE Quote:");
                System.out.println("   LTP: ₹" + (quote.ltpPaisa() / 100.0));
                System.out.println("   Open: ₹" + (quote.openPaisa() / 100.0));
                System.out.println("   High: ₹" + (quote.highPaisa() / 100.0));
                System.out.println("   Low: ₹" + (quote.lowPaisa() / 100.0));
                System.out.println("   Volume: " + quote.volume());
                
                assert quote.ltpPaisa() > 0 : "LTP should be positive";
                assert quote.openPaisa() > 0 : "Open should be positive";
                assert quote.highPaisa() >= quote.lowPaisa() : "High should be >= Low";
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("LIVE: Should fetch fund limits")
    void testGetFundLimits() {
        System.out.println("🔵 Fetching fund limits...");
        
        StepVerifier.create(broker.getFundLimits())
            .assertNext(limits -> {
                System.out.println("✅ Fund Limits:");
                System.out.println("   Available: ₹" + limits.availableBalance());
                System.out.println("   Utilized: ₹" + limits.utilizedMargin());
                System.out.println("   Total: ₹" + limits.totalLimit());
                
                assert limits.availableBalance().compareTo(java.math.BigDecimal.ZERO) >= 0 : "Balance should be non-negative";
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("LIVE: Should fetch holdings")
    void testGetHoldings() {
        System.out.println("🔵 Fetching holdings...");
        
        StepVerifier.create(broker.getHoldings().collectList())
            .assertNext(holdings -> {
                System.out.println("✅ Holdings count: " + holdings.size());
                holdings.forEach(h -> 
                    System.out.println("   - " + h.symbol() + ": " + h.totalQuantity() + " shares")
                );
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("LIVE: Should fetch positions")
    void testGetPositions() {
        System.out.println("🔵 Fetching positions...");
        
        StepVerifier.create(broker.getPositions().collectList())
            .assertNext(positions -> {
                System.out.println("✅ Positions count: " + positions.size());
                positions.forEach(p -> 
                    System.out.println("   - " + p.symbol() + ": " + p.quantity() + " qty, P&L: " + p.unrealizedPnlPaisa())
                );
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("LIVE: Should fetch historical candles (intraday)")
    void testGetHistoricalCandles() {
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        CandleHistoryRequest request = new CandleHistoryRequest(
            key,
            "5m",
            LocalDate.now().minusDays(1),
            LocalDate.now()
        );
        
        System.out.println("🔵 Fetching 5-min candles for RELIANCE (last 1 day)...");
        
        StepVerifier.create(broker.getCandles(request).take(10).collectList())
            .assertNext(candles -> {
                System.out.println("✅ Received " + candles.size() + " candles:");
                candles.forEach(c -> 
                    System.out.println("   O: " + c.openPaisa() + 
                                     ", H: " + c.highPaisa() + 
                                     ", L: " + c.lowPaisa() + 
                                     ", C: " + c.closePaisa() +
                                     ", V: " + c.volume())
                );
                
                assert !candles.isEmpty() : "Should receive at least one candle";
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("LIVE: Should fetch batch LTP for multiple symbols")
    void testGetBatchLtp() {
        List<InstrumentKey> symbols = List.of(
            new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ),
            new InstrumentKey("TCS", ExchangeSegment.NSE_EQ)
        );
        
        System.out.println("🔵 Fetching batch LTP for RELIANCE, TCS...");
        
        StepVerifier.create(broker.getLtpBatch(symbols))
            .assertNext(ltpMap -> {
                System.out.println("✅ Batch LTP:");
                ltpMap.forEach((key, ltp) -> 
                    System.out.println("   " + key.symbol() + ": ₹" + (ltp / 100.0))
                );
                
                assert ltpMap.size() == 2 : "Should have 2 symbols";
                assert ltpMap.values().stream().allMatch(ltp -> ltp > 0) : "All LTPs should be positive";
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("LIVE: Should place and cancel a test order (MARKET BUY 1 qty)")
    void testPlaceAndCancelOrder() {
        // WARNING: This will place a REAL order!
        // Using minimum quantity to minimize risk
        
        OrderRequest request = new OrderRequest(
            "RELIANCE",
            ExchangeSegment.NSE_EQ,
            Side.BUY,
            1, // Minimum quantity
            OrderType.MARKET,
            0,
            0,
            ProductType.INTRADAY,
            Validity.DAY,
            "reactive-live-test-" + System.currentTimeMillis()
        );
        
        System.out.println("🔴 PLACING REAL ORDER (MARKET BUY 1 RELIANCE)...");
        
        String orderId = broker.placeOrder(request)
            .doOnSuccess(id -> System.out.println("✅ Order placed: " + id))
            .block();
        
        assert orderId != null : "Order ID should not be null";
        assert !orderId.isEmpty() : "Order ID should not be empty";
        
        System.out.println("⏳ Waiting 2 seconds for order to be processed...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Check order status
        System.out.println("🔵 Checking order status...");
        StepVerifier.create(broker.getOrderStatus(orderId))
            .assertNext(status -> {
                System.out.println("✅ Order status: " + status);
                assert status != OrderStatus.UNKNOWN : "Status should be known";
            })
            .verifyComplete();
        
        // Cancel the order (if not already traded)
        System.out.println("🔴 CANCELLING ORDER...");
        StepVerifier.create(broker.cancelOrder(orderId))
            .assertNext(cancelled -> {
                System.out.println("✅ Order cancelled: " + cancelled);
            })
            .verifyComplete();
    }
}
