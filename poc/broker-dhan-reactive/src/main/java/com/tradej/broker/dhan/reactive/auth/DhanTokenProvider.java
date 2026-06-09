package com.tradej.broker.dhan.reactive.auth;

import reactor.core.publisher.Mono;

/**
 * Reactive token provider interface for Dhan authentication.
 */
public interface DhanTokenProvider {
    
    /**
     * Ensures a valid token is available, refreshing if necessary.
     * 
     * @return Mono containing the valid access token
     */
    Mono<String> ensureValidReactive();
}
