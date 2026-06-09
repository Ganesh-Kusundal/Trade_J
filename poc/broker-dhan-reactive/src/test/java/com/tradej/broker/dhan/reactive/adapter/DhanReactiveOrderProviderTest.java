package com.tradej.broker.dhan.reactive.adapter;

import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * TDD Tests for DhanReactiveOrderProvider
 * 
 * RED Phase: These tests define the expected behavior of reactive order provider.
 * They should fail initially, then we'll implement the code to make them pass.
 */
@ExtendWith(MockitoExtension.class)
class DhanReactiveOrderProviderTest {
    
    @Mock
    private DhanReactiveHttpClient mockHttpClient;
    
    private DhanReactiveOrderProvider orderProvider;
    
    @BeforeEach
    void setUp() {
        orderProvider = new DhanReactiveOrderProvider(mockHttpClient);
    }
    
    @Test
    void placeOrderShouldReturnOrderId() {
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
        
        String placeOrderResponse = """
            {
                "orderId": "12345678",
                "status": "SUCCESS"
            }
            """;
        
        when(mockHttpClient.postJson(anyString(), any()))
            .thenReturn(Mono.just(new DhanJsonResponse(placeOrderResponse)));
        
        // Act & Assert
        StepVerifier.create(orderProvider.placeOrder(request))
            .expectNext("12345678")
            .verifyComplete();
    }
    
    @Test
    void cancelOrderShouldReturnSuccess() {
        // Arrange
        String cancelResponse = """
            {
                "orderId": "12345678",
                "status": "CANCELLED"
            }
            """;
        
        when(mockHttpClient.deleteJson(anyString()))
            .thenReturn(Mono.just(new DhanJsonResponse(cancelResponse)));
        
        // Act & Assert
        StepVerifier.create(orderProvider.cancelOrder("12345678"))
            .expectNext(true)
            .verifyComplete();
    }
    
    @Test
    void getOrderStatusShouldReturnStatus() {
        // Arrange
        String orderStatusResponse = """
            {
                "orderId": "12345678",
                "orderStatus": "TRADED",
                "filledQuantity": 10,
                "filledPrice": "2456.70"
            }
            """;
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new DhanJsonResponse(orderStatusResponse)));
        
        // Act & Assert
        StepVerifier.create(orderProvider.getOrderStatus("12345678"))
            .expectNext(OrderStatus.TRADED)
            .verifyComplete();
    }
    
    @Test
    void modifyOrderShouldReturnOrderId() {
        // Arrange
        String modifyResponse = """
            {
                "orderId": "12345678",
                "status": "MODIFIED"
            }
            """;
        
        when(mockHttpClient.putJson(anyString(), any()))
            .thenReturn(Mono.just(new DhanJsonResponse(modifyResponse)));
        
        OrderRequest request = new OrderRequest(
            "RELIANCE",
            ExchangeSegment.NSE_EQ,
            Side.BUY,
            10,
            OrderType.LIMIT,
            245000, // 2450.00 in paise
            0,
            ProductType.INTRADAY,
            Validity.DAY,
            null
        );
        
        // Act & Assert
        StepVerifier.create(orderProvider.modifyOrder("12345678", request))
            .expectNext("12345678")
            .verifyComplete();
    }
    
    @Test
    void shouldHandlePlaceOrderFailure() {
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
            .thenReturn(Mono.error(new RuntimeException("Insufficient margin")));
        
        // Act & Assert
        StepVerifier.create(orderProvider.placeOrder(request))
            .expectErrorMessage("Insufficient margin")
            .verify();
    }
    
    @Test
    void shouldHandleCancelOrderFailure() {
        // Arrange
        when(mockHttpClient.deleteJson(anyString()))
            .thenReturn(Mono.error(new RuntimeException("Order not found")));
        
        // Act & Assert
        StepVerifier.create(orderProvider.cancelOrder("99999"))
            .expectErrorMessage("Order not found")
            .verify();
    }
}
