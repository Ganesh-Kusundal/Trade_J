package com.tradej.broker.dhan.reactive.adapter;

import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.reactive.port.ReactivePortfolioProvider;
import com.tradej.core.domain.model.FundLimits;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Reactive portfolio provider for Dhan.
 * 
 * GREEN Phase: Minimal implementation to make tests pass.
 */
public final class DhanReactivePortfolioProvider implements ReactivePortfolioProvider {
    
    private static final Logger log = LoggerFactory.getLogger(DhanReactivePortfolioProvider.class);
    
    private final DhanReactiveHttpClient httpClient;
    
    public DhanReactivePortfolioProvider(DhanReactiveHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    @Override
    public Flux<Holding> getHoldings() {
        return httpClient.getJson("/portfolio/holdings")
            .flatMapMany(response -> {
                if (response.raw().isArray()) {
                    return Flux.fromIterable(parseHoldings(response));
                }
                return Flux.just(parseHolding(response));
            })
            .doOnError(ex -> log.error("Failed to fetch holdings: {}", ex.getMessage()));
    }
    
    @Override
    public Flux<Position> getPositions() {
        return httpClient.getJson("/portfolio/positions")
            .flatMapMany(response -> {
                if (response.raw().isArray()) {
                    return Flux.fromIterable(parsePositions(response));
                }
                return Flux.just(parsePosition(response));
            })
            .doOnError(ex -> log.error("Failed to fetch positions: {}", ex.getMessage()));
    }
    
    @Override
    public Mono<FundLimits> getFundLimits() {
        return httpClient.getJson("/fundlimits")
            .map(response -> {
                BigDecimal available = new BigDecimal(response.get("availableBalance").asText());
                BigDecimal utilized = new BigDecimal(response.get("utilizedMargin").asText());
                BigDecimal total = new BigDecimal(response.get("totalLimit").asText());
                
                return new FundLimits(available, utilized, total);
            })
            .doOnError(ex -> log.error("Failed to fetch fund limits: {}", ex.getMessage()));
    }
    
    private java.util.List<Holding> parseHoldings(DhanJsonResponse response) {
        java.util.List<Holding> holdings = new java.util.ArrayList<>();
        response.raw().forEach(node -> {
            if (node.isObject()) {
                holdings.add(parseHolding(new DhanJsonResponse(node.toString())));
            }
        });
        return holdings;
    }
    
    private Holding parseHolding(DhanJsonResponse response) {
        String symbol = response.get("symbol").asText();
        long quantity = response.has("quantity") ? response.get("quantity").asLong() : 0;
        long avgPrice = response.has("averagePrice") 
            ? Math.round(Double.parseDouble(response.get("averagePrice").asText()) * 100)
            : 0;
        
        return new Holding(
            symbol,
            ExchangeSegment.NSE_EQ, // Simplified
            quantity,
            quantity,
            0,
            avgPrice
        );
    }
    
    private java.util.List<Position> parsePositions(DhanJsonResponse response) {
        java.util.List<Position> positions = new java.util.ArrayList<>();
        response.raw().forEach(node -> {
            if (node.isObject()) {
                positions.add(parsePosition(new DhanJsonResponse(node.toString())));
            }
        });
        return positions;
    }
    
    private Position parsePosition(DhanJsonResponse response) {
        String symbol = response.get("symbol").asText();
        long quantity = response.has("quantity") ? response.get("quantity").asLong() : 0;
        long avgPrice = response.has("averagePrice") 
            ? Math.round(Double.parseDouble(response.get("averagePrice").asText()) * 100)
            : 0;
        long unrealizedPnl = response.has("unrealizedPnl")
            ? Math.round(Double.parseDouble(response.get("unrealizedPnl").asText()) * 100)
            : 0;
        
        return new Position(
            symbol,
            ExchangeSegment.NSE_EQ,
            Side.BUY, // Simplified
            quantity,
            avgPrice,
            0,
            unrealizedPnl
        );
    }
}
