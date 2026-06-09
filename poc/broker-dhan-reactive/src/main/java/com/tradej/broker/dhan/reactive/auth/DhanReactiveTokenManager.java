package com.tradej.broker.dhan.reactive.auth;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Reactive token manager for Dhan authentication.
 * 
 * Handles token lifecycle:
 * - Caches valid tokens
 * - Auto-refreshes expired tokens
 * - Thread-safe token updates
 * 
 * GREEN Phase: Minimal implementation to make tests pass.
 */
public class DhanReactiveTokenManager implements DhanTokenProvider {
    
    private static final Logger log = LoggerFactory.getLogger(DhanReactiveTokenManager.class);
    
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 60; // Refresh 60s before expiry
    
    private final DhanReactiveHttpClient authClient;
    private final DhanReactiveConnectionSettings settings;
    
    private volatile String cachedToken;
    private volatile Instant tokenExpiry;
    
    public DhanReactiveTokenManager(
            DhanReactiveHttpClient authClient,
            DhanReactiveConnectionSettings settings
    ) {
        this.authClient = authClient;
        this.settings = settings;
    }
    
    /**
     * Ensures a valid token is available, refreshing if necessary.
     */
    @Override
    public Mono<String> ensureValidReactive() {
        return Mono.defer(() -> {
            if (isTokenValid()) {
                return Mono.just(cachedToken);
            }
            return refreshTokenReactive();
        });
    }
    
    /**
     * Cache a token with explicit expiry time.
     * Used for testing and token rotation scenarios.
     */
    public void cacheToken(String token, Instant expiry) {
        this.cachedToken = token;
        this.tokenExpiry = expiry;
    }
    
    /**
     * Check if the current token is valid (not expired and within buffer zone).
     */
    private boolean isTokenValid() {
        if (cachedToken == null || tokenExpiry == null) {
            return false;
        }
        
        Instant now = Instant.now();
        Instant expiryWithBuffer = tokenExpiry.minusSeconds(TOKEN_REFRESH_BUFFER_SECONDS);
        
        return now.isBefore(expiryWithBuffer);
    }
    
    /**
     * Refresh the token by calling Dhan auth API.
     */
    private Mono<String> refreshTokenReactive() {
        log.info("Refreshing Dhan access token");
        
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("client_id", settings.clientId());
        payload.put("api_secret", settings.apiSecret());
        
        return authClient.postJson(settings.tokenUrl(), payload)
            .map(response -> {
                String newToken = response.get("access_token").asText();
                long expiresIn = response.has("expires_in") 
                    ? response.get("expires_in").asLong() 
                    : 86400; // Default 24 hours
                
                // Cache the new token
                cachedToken = newToken;
                tokenExpiry = Instant.now().plusSeconds(expiresIn);
                
                log.info("Token refreshed successfully, expires in {} seconds", expiresIn);
                return newToken;
            })
            .doOnError(error -> log.error("Failed to refresh token: {}", error.getMessage()));
    }
}
