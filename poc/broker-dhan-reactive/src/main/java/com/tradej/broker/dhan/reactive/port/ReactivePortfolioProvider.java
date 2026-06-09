package com.tradej.broker.dhan.reactive.port;

import com.tradej.core.domain.model.FundLimits;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive portfolio provider interface.
 */
public interface ReactivePortfolioProvider {
    
    /**
     * Get all holdings (delivery positions).
     */
    Flux<Holding> getHoldings();
    
    /**
     * Get all positions (intraday + carry forward).
     */
    Flux<Position> getPositions();
    
    /**
     * Get fund limits and margin utilization.
     */
    Mono<FundLimits> getFundLimits();
}
