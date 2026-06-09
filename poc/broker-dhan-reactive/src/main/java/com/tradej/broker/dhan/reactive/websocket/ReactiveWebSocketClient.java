package com.tradej.broker.dhan.reactive.websocket;

import com.tradej.core.domain.model.InstrumentKey;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive WebSocket client interface for Dhan.
 */
public interface ReactiveWebSocketClient {
    
    /**
     * Subscribe to LTP (Last Traded Price) updates.
     */
    Flux<DhanReactiveWebSocketClient.MarketDataUpdate> subscribeToLtp(List<InstrumentKey> instruments);
    
    /**
     * Subscribe to full quote updates (OHLCV).
     */
    Flux<DhanReactiveWebSocketClient.MarketDataUpdate> subscribeToQuote(List<InstrumentKey> instruments);
    
    /**
     * Subscribe to market depth (order book).
     */
    Flux<DhanReactiveWebSocketClient.MarketDataUpdate> subscribeToDepth(List<InstrumentKey> instruments);
}
