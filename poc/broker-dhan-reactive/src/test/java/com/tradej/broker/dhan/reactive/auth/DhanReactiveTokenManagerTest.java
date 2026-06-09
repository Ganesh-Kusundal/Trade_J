package com.tradej.broker.dhan.reactive.auth;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TDD Tests for DhanReactiveTokenManager
 * 
 * RED Phase: These tests define the expected behavior of reactive token management.
 * They should fail initially, then we'll implement the code to make them pass.
 */
@ExtendWith(MockitoExtension.class)
class DhanReactiveTokenManagerTest {
    
    @Mock
    private DhanReactiveHttpClient mockHttpClient;
    
    private DhanReactiveConnectionSettings settings;
    private DhanReactiveTokenManager tokenManager;
    
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
        
        tokenManager = new DhanReactiveTokenManager(mockHttpClient, settings);
    }
    
    @Test
    void shouldReturnCachedTokenWhenValid() {
        // Arrange - set a valid token (expires in 1 hour)
        tokenManager.cacheToken("valid-token", Instant.now().plusSeconds(3600));
        
        // Act & Assert
        StepVerifier.create(tokenManager.ensureValidReactive())
            .expectNext("valid-token")
            .verifyComplete();
        
        // Verify no HTTP call was made (token is cached and valid)
        verifyNoInteractions(mockHttpClient);
    }
    
    @Test
    void shouldRefreshTokenWhenExpired() {
        // Arrange - set expired token
        tokenManager.cacheToken("expired-token", Instant.now().minusSeconds(10));
        
        String refreshTokenResponse = """
            {
                "access_token": "new-fresh-token",
                "expires_in": 86400
            }
            """;
        
        when(mockHttpClient.postJson(anyString(), any()))
            .thenReturn(Mono.just(new DhanJsonResponse(refreshTokenResponse)));
        
        // Act & Assert
        StepVerifier.create(tokenManager.ensureValidReactive())
            .expectNext("new-fresh-token")
            .verifyComplete();
        
        // Verify HTTP call was made to refresh token
        verify(mockHttpClient, times(1)).postJson(anyString(), any());
    }
    
    @Test
    void shouldRefreshTokenWhenNull() {
        // Arrange - no token cached (null)
        
        String refreshTokenResponse = """
            {
                "access_token": "first-token",
                "expires_in": 86400
            }
            """;
        
        when(mockHttpClient.postJson(anyString(), any()))
            .thenReturn(Mono.just(new DhanJsonResponse(refreshTokenResponse)));
        
        // Act & Assert
        StepVerifier.create(tokenManager.ensureValidReactive())
            .expectNext("first-token")
            .verifyComplete();
        
        // Verify HTTP call was made
        verify(mockHttpClient, times(1)).postJson(anyString(), any());
    }
    
    @Test
    void shouldHandleRefreshFailure() {
        // Arrange - expired token
        tokenManager.cacheToken("expired-token", Instant.now().minusSeconds(10));
        
        when(mockHttpClient.postJson(anyString(), any()))
            .thenReturn(Mono.error(new RuntimeException("Auth failed: Invalid credentials")));
        
        // Act & Assert
        StepVerifier.create(tokenManager.ensureValidReactive())
            .expectErrorMessage("Auth failed: Invalid credentials")
            .verify();
    }
    
    @Test
    void shouldNotRefreshTokenWhenExpiringSoonButStillValid() {
        // Arrange - token expires in 30 seconds (within buffer zone)
        tokenManager.cacheToken("almost-expired", Instant.now().plusSeconds(30));
        
        String refreshTokenResponse = """
            {
                "access_token": "new-token",
                "expires_in": 86400
            }
            """;
        
        when(mockHttpClient.postJson(anyString(), any()))
            .thenReturn(Mono.just(new DhanJsonResponse(refreshTokenResponse)));
        
        // Act & Assert - should refresh because it's within 60-second buffer
        StepVerifier.create(tokenManager.ensureValidReactive())
            .expectNext("new-token")
            .verifyComplete();
        
        verify(mockHttpClient, times(1)).postJson(anyString(), any());
    }
    
    @Test
    void shouldRefreshTokenMultipleTimes() {
        // Arrange - start with expired token
        tokenManager.cacheToken("expired", Instant.now().minusSeconds(10));
        
        String firstRefresh = """
            {
                "access_token": "token-1",
                "expires_in": 86400
            }
            """;
        
        when(mockHttpClient.postJson(anyString(), any()))
            .thenReturn(Mono.just(new DhanJsonResponse(firstRefresh)));
        
        // Act - first refresh
        StepVerifier.create(tokenManager.ensureValidReactive())
            .expectNext("token-1")
            .verifyComplete();
        
        // Act - second call should use cached token (no HTTP call)
        StepVerifier.create(tokenManager.ensureValidReactive())
            .expectNext("token-1")
            .verifyComplete();
        
        // Verify only one HTTP call was made
        verify(mockHttpClient, times(1)).postJson(anyString(), any());
    }
    
    @Test
    void shouldBuildCorrectRefreshPayload() {
        // Arrange
        tokenManager.cacheToken("expired", Instant.now().minusSeconds(10));
        
        String refreshTokenResponse = """
            {
                "access_token": "new-token",
                "expires_in": 86400
            }
            """;
        
        when(mockHttpClient.postJson(anyString(), any(ObjectNode.class)))
            .thenAnswer(invocation -> {
                ObjectNode payload = invocation.getArgument(1);
                // Verify payload contains correct credentials
                assert payload.get("client_id").asText().equals("test-client-id");
                assert payload.get("api_secret").asText().equals("test-api-secret");
                return Mono.just(new DhanJsonResponse(refreshTokenResponse));
            });
        
        // Act & Assert
        StepVerifier.create(tokenManager.ensureValidReactive())
            .expectNext("new-token")
            .verifyComplete();
    }
}
