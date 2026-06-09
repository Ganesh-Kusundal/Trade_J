package com.tradej.broker.dhan.reactive;

import com.tradej.broker.dhan.reactive.adapter.DhanReactiveMarketDataProvider;
import com.tradej.broker.dhan.reactive.adapter.DhanReactiveOrderProvider;
import com.tradej.broker.dhan.reactive.adapter.DhanReactivePortfolioProvider;
import com.tradej.broker.dhan.reactive.auth.DhanTokenProvider;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentResolver;
import com.tradej.broker.dhan.reactive.websocket.DhanReactiveWebSocketClient;
import com.tradej.core.domain.model.Candle;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * TDD Tests for DhanReactiveBroker Facade
 * 
 * RED Phase: These tests define the expected behavior of the unified reactive broker facade.
 * They should fail initially, then we'll implement the code to make them pass.
 */
@ExtendWith(MockitoExtension.class)
class DhanReactiveBrokerTest {
    
    @Mock
    private DhanReactiveHttpClient mockHttpClient;
    
    @Mock
    private DhanTokenProvider mockTokenProvider;
    
    @Mock
    private DhanInstrumentResolver mockResolver;
    
    private DhanReactiveConnectionSettings settings;
    private DhanReactiveBroker broker;
    
    @BeforeEach
    void setUp() {
        settings = new DhanReactiveConnectionSettings(
            "test-client-id",
            "test-api-secret",
            true,
            10,
            Duration.ofSeconds(5),
            Duration.ofSeconds(10)
        );
        
        // Create real providers with mocked HTTP client
        DhanReactiveMarketDataProvider marketDataProvider = new DhanReactiveMarketDataProvider(
            mockHttpClient, mockResolver
        );
        DhanReactiveOrderProvider orderProvider = new DhanReactiveOrderProvider(mockHttpClient);
        DhanReactivePortfolioProvider portfolioProvider = new DhanReactivePortfolioProvider(mockHttpClient);
        DhanReactiveWebSocketClient webSocketClient = new DhanReactiveWebSocketClient(
            settings, mockTokenProvider
        );
        
        broker = new DhanReactiveBroker(
            marketDataProvider,
            orderProvider,
            portfolioProvider,
            webSocketClient
        );
    }
    
    @Test
    void getLtpShouldDelegateToMarketDataProvider() {
        // Arrange
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        when(mockResolver.resolve(key))
            .thenReturn(new com.tradej.broker.dhan.reactive.instrument.DhanInstrumentDefinition(
                "NSE_EQ", "2885", "RELIANCE"
            ));
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse(
                "{\"last_price\": \"2456.70\"}"
            )));
        
        // Act & Assert
        StepVerifier.create(broker.getLtpPaisa(key))
            .expectNext(245670L)
            .verifyComplete();
    }
    
    @Test
    void placeOrderShouldDelegateToOrderProvider() {
        // Arrange
        OrderRequest request = new OrderRequest(
            "RELIANCE",
            ExchangeSegment.NSE_EQ,
            Side.BUY,
            10,
            OrderType.MARKET,
            0,
            0,
            ProductType.INTRADAY,
            Validity.DAY,
            null
        );
        
        when(mockHttpClient.postJson(anyString(), any()))
            .thenReturn(Mono.just(new com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse(
                "{\"orderId\": \"12345\"}"
            )));
        
        // Act & Assert
        StepVerifier.create(broker.placeOrder(request))
            .expectNext("12345")
            .verifyComplete();
    }
    
    @Test
    void cancelOrderShouldDelegateToOrderProvider() {
        // Arrange
        when(mockHttpClient.deleteJson(anyString()))
            .thenReturn(Mono.just(new com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse(
                "{\"status\": \"CANCELLED\"}"
            )));
        
        // Act & Assert
        StepVerifier.create(broker.cancelOrder("12345"))
            .expectNext(true)
            .verifyComplete();
    }
    
    @Test
    void getOrderStatusShouldDelegateToOrderProvider() {
        // Arrange
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse(
                "{\"orderStatus\": \"TRADED\"}"
            )));
        
        // Act & Assert
        StepVerifier.create(broker.getOrderStatus("12345"))
            .expectNext(OrderStatus.TRADED)
            .verifyComplete();
    }
    
    @Test
    void getHoldingsShouldDelegateToPortfolioProvider() {
        // Arrange
        String holdingsJson = """
            [
                {
                    "symbol": "RELIANCE",
                    "quantity": 10,
                    "averagePrice": "2450.00"
                }
            ]
            """;
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse(holdingsJson)));
        
        // Act & Assert
        StepVerifier.create(broker.getHoldings())
            .expectNextCount(1)
            .verifyComplete();
    }
    
    @Test
    void getPositionsShouldDelegateToPortfolioProvider() {
        // Arrange
        String positionsJson = """
            [
                {
                    "symbol": "RELIANCE",
                    "quantity": 10,
                    "averagePrice": "2450.00",
                    "unrealizedPnl": "67.00"
                }
            ]
            """;
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse(positionsJson)));
        
        // Act & Assert
        StepVerifier.create(broker.getPositions())
            .expectNextCount(1)
            .verifyComplete();
    }
    
    @Test
    void getFundLimitsShouldDelegateToPortfolioProvider() {
        // Arrange
        String fundLimitsJson = """
            {
                "availableBalance": "100000.00",
                "utilizedMargin": "25000.00",
                "totalLimit": "125000.00"
            }
            """;
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse(fundLimitsJson)));
        
        // Act & Assert
        StepVerifier.create(broker.getFundLimits())
            .expectNextMatches(limits -> 
                limits.availableBalance().compareTo(new BigDecimal("100000.00")) == 0
            )
            .verifyComplete();
    }
    
    @Test
    void getCandlesShouldDelegateToMarketDataProvider() {
        // Arrange
        CandleHistoryRequest request = new CandleHistoryRequest(
            new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ),
            "1m",
            LocalDate.now().minusDays(1),
            LocalDate.now()
        );
        
        when(mockResolver.resolve(request.instrument()))
            .thenReturn(new com.tradej.broker.dhan.reactive.instrument.DhanInstrumentDefinition(
                "NSE_EQ", "2885", "RELIANCE"
            ));
        
        String candlesJson = """
            [
                {
                    "open": "2440.00",
                    "high": "2445.00",
                    "low": "2438.00",
                    "close": "2442.00",
                    "volume": "1000"
                }
            ]
            """;
        
        when(mockHttpClient.getJsonStream(anyString()))
            .thenReturn(Flux.just(new com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse(candlesJson)));
        
        // Act & Assert
        StepVerifier.create(broker.getCandles(request))
            .expectNextCount(1)
            .verifyComplete();
    }
}
