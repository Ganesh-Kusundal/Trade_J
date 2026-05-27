package com.tradej.app.config;

import com.tradej.core.domain.port.EventBus;
import com.tradej.disruptor.DisruptorEventBus;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.StrategyEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Disruptor-based event bus with its handler pipeline.
 *
 * <p>This bean wires together handlers from {@link RiskConfiguration},
 * {@link StrategyConfiguration}, and {@link ExecutionConfiguration} into
 * the Disruptor ring buffer pipeline.
 *
 * <p>Extracted from {@link TradingRuntimeConfiguration} to separate event
 * bus wiring from general application concerns (Phase A.2).
 */
@Configuration
public class EventBusConfiguration {

    @Bean
    EventBus eventBus(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler
    ) {
        return new DisruptorEventBus(positionRiskHandler, candleAggregationService, strategyEngine, executionHandler);
    }
}
