package com.tradej.broker.dhan.reactive.websocket;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Quote;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * Reactive WebSocket client interface.
 */
public interface ReactiveWebSocketClient {
    
    /**
     * Connect to WebSocket server.
     */
    Mono<Void> connect();
    
    /**
     * Subscribe to LTP (Last Traded Price) stream.
     */
    Flux<Quote> subscribeToLtp(Collection<InstrumentKey> instruments);
    
    /**
     * Subscribe to full quote stream.
     */
    Flux<Quote> subscribeToQuote(Collection<InstrumentKey> instruments);
    
    /**
     * Unsubscribe from instruments.
     */
    Mono<Void> unsubscribe(Collection<InstrumentKey> instruments);
    
    /**
     * Disconnect from WebSocket server.
     */
    Mono<Void> disconnect();
}
