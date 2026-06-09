package com.tradej.broker.dhan.reactive;

import com.tradej.broker.dhan.reactive.adapter.DhanReactiveMarketDataProvider;
import com.tradej.broker.dhan.reactive.adapter.DhanReactiveOrderProvider;
import com.tradej.broker.dhan.reactive.adapter.DhanReactivePortfolioProvider;
import com.tradej.broker.dhan.reactive.websocket.DhanReactiveWebSocketClient;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.FundLimits;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.OrderStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Unified reactive broker facade for Dhan.
 * 
 * This is the main entry point for all reactive broker operations.
 * It delegates to specialized providers for market data, orders, portfolio, and WebSocket.
 * 
 * GREEN Phase: Minimal implementation to make tests pass.
 */
public final class DhanReactiveBroker {
    
    private final DhanReactiveMarketDataProvider marketDataProvider;
    private final DhanReactiveOrderProvider orderProvider;
    private final DhanReactivePortfolioProvider portfolioProvider;
    private final DhanReactiveWebSocketClient webSocketClient;
    
    public DhanReactiveBroker(
            DhanReactiveMarketDataProvider marketDataProvider,
            DhanReactiveOrderProvider orderProvider,
            DhanReactivePortfolioProvider portfolioProvider,
            DhanReactiveWebSocketClient webSocketClient
    ) {
        this.marketDataProvider = marketDataProvider;
        this.orderProvider = orderProvider;
        this.portfolioProvider = portfolioProvider;
        this.webSocketClient = webSocketClient;
    }
    
    // ========== Market Data Operations ==========
    
    /**
     * Get last traded price in paise.
     */
    public Mono<Long> getLtpPaisa(InstrumentKey instrumentKey) {
        return marketDataProvider.getLtpPaisa(instrumentKey);
    }
    
    /**
     * Get full quote snapshot.
     */
    public Mono<Quote> getQuote(InstrumentKey instrumentKey) {
        return marketDataProvider.getQuote(instrumentKey);
    }
    
    /**
     * Get historical candles.
     */
    public Flux<Candle> getCandles(CandleHistoryRequest request) {
        return marketDataProvider.getCandles(request);
    }
    
    /**
     * Get LTP for multiple instruments in batch.
     */
    public Mono<Map<InstrumentKey, Long>> getLtpBatch(Collection<InstrumentKey> instrumentKeys) {
        return marketDataProvider.getLtpBatch(instrumentKeys);
    }
    
    // ========== Order Operations ==========
    
    /**
     * Place a new order.
     */
    public Mono<String> placeOrder(OrderRequest request) {
        return orderProvider.placeOrder(request);
    }
    
    /**
     * Modify an existing order.
     */
    public Mono<String> modifyOrder(String orderId, OrderRequest request) {
        return orderProvider.modifyOrder(orderId, request);
    }
    
    /**
     * Cancel an order.
     */
    public Mono<Boolean> cancelOrder(String orderId) {
        return orderProvider.cancelOrder(orderId);
    }
    
    /**
     * Get order status.
     */
    public Mono<OrderStatus> getOrderStatus(String orderId) {
        return orderProvider.getOrderStatus(orderId);
    }
    
    // ========== Portfolio Operations ==========
    
    /**
     * Get all holdings (delivery positions).
     */
    public Flux<Holding> getHoldings() {
        return portfolioProvider.getHoldings();
    }
    
    /**
     * Get all positions (intraday + carry forward).
     */
    public Flux<Position> getPositions() {
        return portfolioProvider.getPositions();
    }
    
    /**
     * Get fund limits and margin utilization.
     */
    public Mono<FundLimits> getFundLimits() {
        return portfolioProvider.getFundLimits();
    }
    
    // ========== WebSocket Operations ==========
    
    /**
     * Connect to WebSocket server.
     */
    public Mono<Void> connectWebSocket() {
        return webSocketClient.connect();
    }
    
    /**
     * Subscribe to LTP stream via WebSocket.
     */
    public Flux<Quote> subscribeToLtpStream(Collection<InstrumentKey> instruments) {
        return webSocketClient.subscribeToLtp(instruments);
    }
    
    /**
     * Disconnect from WebSocket server.
     */
    public Mono<Void> disconnectWebSocket() {
        return webSocketClient.disconnect();
    }
}
