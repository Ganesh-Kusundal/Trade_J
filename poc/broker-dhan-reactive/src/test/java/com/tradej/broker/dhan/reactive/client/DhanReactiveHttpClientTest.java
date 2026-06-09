package com.tradej.broker.dhan.reactive.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tradej.broker.dhan.reactive.auth.DhanTokenProvider;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * TDD Tests for DhanReactiveHttpClient
 * 
 * RED Phase: These tests define the expected behavior of the reactive HTTP client.
 * They should fail initially, then we'll implement the code to make them pass.
 */
@ExtendWith(MockitoExtension.class)
class DhanReactiveHttpClientTest {
    
    private MockWebServer mockWebServer;
    
    @Mock
    private DhanTokenProvider tokenProvider;
    
    private DhanReactiveHttpClient client;
    private DhanReactiveConnectionSettings settings;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        settings = new DhanReactiveConnectionSettings(
            "test-client-id",
            "test-api-secret",
            true, // sandbox mode
            10, // max connections
            Duration.ofSeconds(5), // connection timeout
            Duration.ofSeconds(10) // response timeout
        );
        
        // Default: token provider returns valid token
        when(tokenProvider.ensureValidReactive())
            .thenReturn(Mono.just("test-access-token"));
        
        WebClient webClient = WebClient.builder()
            .baseUrl(mockWebServer.url("/").toString())
            .build();
        
        client = new DhanReactiveHttpClient(webClient, tokenProvider, settings);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    void postJsonShouldReturnResponseWithCorrectHeaders() {
        // Arrange
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("test", "value");
        
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"success\": true}")
            .addHeader("Content-Type", "application/json"));
        
        // Act & Assert
        StepVerifier.create(client.postJson("/test", payload))
            .expectNextMatches(response -> response.has("success"))
            .verifyComplete();
        
        // Verify request headers
        try {
            RecordedRequest request = mockWebServer.takeRequest();
            assertEquals("test-access-token", request.getHeader("access-token"));
            assertEquals("test-client-id", request.getHeader("client-id"));
            assertEquals("POST", request.getMethod());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
    
    @Test
    void getJsonShouldReturnResponse() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"data\": \"test-value\"}")
            .addHeader("Content-Type", "application/json"));
        
        // Act & Assert
        StepVerifier.create(client.getJson("/test"))
            .expectNextMatches(response -> 
                response.get("data").asText().equals("test-value"))
            .verifyComplete();
    }
    
    @Test
    void deleteJsonShouldReturnResponse() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"deleted\": true}")
            .addHeader("Content-Type", "application/json"));
        
        // Act & Assert
        StepVerifier.create(client.deleteJson("/test/123"))
            .expectNextMatches(response -> response.has("deleted"))
            .verifyComplete();
    }
    
    @Test
    void shouldRetryOn500ServerError() {
        // Arrange - First request fails, second succeeds
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"success\": true}")
            .addHeader("Content-Type", "application/json"));
        
        // Act & Assert
        StepVerifier.create(client.getJson("/test"))
            .expectNextMatches(response -> response.has("success"))
            .verifyComplete();
        
        // Verify retry happened
        assertEquals(2, mockWebServer.getRequestCount());
    }
    
    @Test
    void shouldFailImmediatelyOn400ClientError() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(400)
            .setBody("{\"error\": \"Bad request\"}"));
        
        // Act & Assert - Should NOT retry
        StepVerifier.create(client.getJson("/test"))
            .expectError()
            .verify();
        
        // Verify no retry happened
        assertEquals(1, mockWebServer.getRequestCount());
    }
    
    @Test
    void shouldFailWhenTokenProviderFails() {
        // Arrange
        when(tokenProvider.ensureValidReactive())
            .thenReturn(Mono.error(new RuntimeException("Token expired")));
        
        // Act & Assert
        StepVerifier.create(client.getJson("/test"))
            .expectErrorMessage("Token expired")
            .verify();
        
        // Verify no HTTP request was made
        assertEquals(0, mockWebServer.getRequestCount());
    }
    
    @Test
    void shouldHandleEmptyResponse() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setBody("{}")
            .addHeader("Content-Type", "application/json"));
        
        // Act & Assert
        StepVerifier.create(client.getJson("/test"))
            .expectNextMatches(response -> !response.has("error"))
            .verifyComplete();
    }
}
