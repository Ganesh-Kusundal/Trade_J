package com.tradej.broker.dhan.reactive.adapter;

import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import com.tradej.core.domain.model.FundLimits;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * TDD Tests for DhanReactivePortfolioProvider
 * 
 * RED Phase: These tests define the expected behavior of reactive portfolio provider.
 * They should fail initially, then we'll implement the code to make them pass.
 */
@ExtendWith(MockitoExtension.class)
class DhanReactivePortfolioProviderTest {
    
    @Mock
    private DhanReactiveHttpClient mockHttpClient;
    
    private DhanReactivePortfolioProvider portfolioProvider;
    
    @BeforeEach
    void setUp() {
        portfolioProvider = new DhanReactivePortfolioProvider(mockHttpClient);
    }
    
    @Test
    void getHoldingsShouldReturnHoldings() {
        // Arrange
        String holdingsResponse = """
            [
                {
                    "symbol": "RELIANCE",
                    "quantity": 10,
                    "averagePrice": "2450.00",
                    "currentPrice": "2456.70",
                    "pnl": "67.00"
                },
                {
                    "symbol": "TCS",
                    "quantity": 5,
                    "averagePrice": "3890.00",
                    "currentPrice": "3900.50",
                    "pnl": "52.50"
                }
            ]
            """;
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new DhanJsonResponse(holdingsResponse)));
        
        // Act & Assert
        StepVerifier.create(portfolioProvider.getHoldings())
            .expectNextCount(2)
            .verifyComplete();
    }
    
    @Test
    void getPositionsShouldReturnPositions() {
        // Arrange
        String positionsResponse = """
            [
                {
                    "symbol": "RELIANCE",
                    "quantity": 10,
                    "averagePrice": "2450.00",
                    "currentPrice": "2456.70",
                    "realizedPnl": "0.00",
                    "unrealizedPnl": "67.00",
                    "productType": "INTRADAY"
                }
            ]
            """;
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new DhanJsonResponse(positionsResponse)));
        
        // Act & Assert
        StepVerifier.create(portfolioProvider.getPositions())
            .expectNextCount(1)
            .verifyComplete();
    }
    
    @Test
    void getFundLimitsShouldReturnLimits() {
        // Arrange
        String fundLimitsResponse = """
            {
                "availableBalance": "100000.00",
                "utilizedMargin": "25000.00",
                "totalLimit": "125000.00"
            }
            """;
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new DhanJsonResponse(fundLimitsResponse)));
        
        // Act & Assert
        StepVerifier.create(portfolioProvider.getFundLimits())
            .expectNextMatches(limits -> 
                limits.availableBalance().compareTo(new BigDecimal("100000.00")) == 0 &&
                limits.utilizedMargin().compareTo(new BigDecimal("25000.00")) == 0
            )
            .verifyComplete();
    }
    
    @Test
    void shouldHandleEmptyHoldings() {
        // Arrange
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new DhanJsonResponse("[]")));
        
        // Act & Assert
        StepVerifier.create(portfolioProvider.getHoldings())
            .expectNextCount(0)
            .verifyComplete();
    }
    
    @Test
    void shouldHandleApiError() {
        // Arrange
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.error(new RuntimeException("API error")));
        
        // Act & Assert
        StepVerifier.create(portfolioProvider.getHoldings())
            .expectErrorMessage("API error")
            .verify();
    }
    
    @Test
    void getHoldingsShouldCalculatePnl() {
        // Arrange
        String holdingsResponse = """
            [
                {
                    "symbol": "RELIANCE",
                    "quantity": 10,
                    "averagePrice": "2450.00"
                }
            ]
            """;
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new DhanJsonResponse(holdingsResponse)));
        
        // Act & Assert
        StepVerifier.create(portfolioProvider.getHoldings())
            .expectNextMatches(holding -> 
                holding.symbol().equals("RELIANCE") &&
                holding.totalQuantity() == 10 &&
                holding.averagePricePaisa() == 245000
            )
            .verifyComplete();
    }
}
