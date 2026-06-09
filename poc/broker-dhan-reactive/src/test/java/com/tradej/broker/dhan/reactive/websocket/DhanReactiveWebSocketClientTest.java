package com.tradej.broker.dhan.reactive.websocket;

import com.tradej.broker.dhan.reactive.auth.DhanTokenProvider;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * TDD Tests for DhanReactiveWebSocketClient
 * 
 * RED Phase: These tests define the expected behavior of reactive WebSocket client.
 * They should fail initially, then we'll implement the code to make them pass.
 */
@ExtendWith(MockitoExtension.class)
class DhanReactiveWebSocketClientTest {
    
    @Mock
    private DhanTokenProvider mockTokenProvider;
    
    private DhanReactiveConnectionSettings settings;
    
    @BeforeEach
    void setUp() {
        settings = new DhanReactiveConnectionSettings(
            "test-client-id",
            "test-api-secret",
            true, // sandbox mode
            10,
            Duration.ofSeconds(5),
            Duration.ofSeconds(10)
        );
    }
    
    @Test
    void connectShouldEmitConnectionEvent() {
        // Arrange
        when(mockTokenProvider.ensureValidReactive())
            .thenReturn(Mono.just("test-token"));
        
        DhanReactiveWebSocketClient client = new DhanReactiveWebSocketClient(
            settings,
            mockTokenProvider
        );
        
        // Act & Assert - should complete without error
        StepVerifier.create(client.connect())
            .verifyComplete();
    }
    
    @Test
    void subscribeToLtpShouldReturnStream() {
        // Arrange
        DhanReactiveWebSocketClient client = new DhanReactiveWebSocketClient(
            settings,
            mockTokenProvider
        );
        
        List<InstrumentKey> instruments = List.of(
            new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ)
        );
        
        // Act & Assert - should return a flux that can be subscribed to
        StepVerifier.create(client.subscribeToLtp(instruments).take(Duration.ofMillis(100)))
            .thenCancel()
            .verify();
    }
    
    @Test
    void subscribeToQuoteShouldReturnStream() {
        // Arrange
        DhanReactiveWebSocketClient client = new DhanReactiveWebSocketClient(
            settings,
            mockTokenProvider
        );
        
        List<InstrumentKey> instruments = List.of(
            new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ)
        );
        
        // Act & Assert
        StepVerifier.create(client.subscribeToQuote(instruments).take(Duration.ofMillis(100)))
            .thenCancel()
            .verify();
    }
    
    @Test
    void unsubscribeShouldComplete() {
        // Arrange
        DhanReactiveWebSocketClient client = new DhanReactiveWebSocketClient(
            settings,
            mockTokenProvider
        );
        
        List<InstrumentKey> instruments = List.of(
            new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ)
        );
        
        // Act & Assert
        StepVerifier.create(client.unsubscribe(instruments))
            .verifyComplete();
    }
    
    @Test
    void disconnectShouldComplete() {
        // Arrange
        DhanReactiveWebSocketClient client = new DhanReactiveWebSocketClient(
            settings,
            mockTokenProvider
        );
        
        // Act & Assert
        StepVerifier.create(client.disconnect())
            .verifyComplete();
    }
    
    @Test
    void shouldHandleConnectionFailure() {
        // Arrange - use valid settings but token provider will fail
        when(mockTokenProvider.ensureValidReactive())
            .thenReturn(Mono.error(new RuntimeException("Token refresh failed")));
        
        DhanReactiveWebSocketClient client = new DhanReactiveWebSocketClient(
            settings,
            mockTokenProvider
        );
        
        // Act & Assert - should handle gracefully
        StepVerifier.create(client.connect())
            .expectErrorMessage("Token refresh failed")
            .verify();
    }
}
