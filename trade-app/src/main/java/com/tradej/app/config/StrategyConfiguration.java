package com.tradej.app.config;

import com.tradej.strategy.api.StrategyPlugin;
import com.tradej.strategy.service.StrategyEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures strategy-layer services.
 *
 * <p>{@link com.tradej.strategy.service.CandleAggregationService} is now
 * discovered via {@code @Service} component scanning (Phase A.3). Only beans
 * that require explicit wiring remain here.
 *
 * <p>Extracted from {@link TradingRuntimeConfiguration} to separate strategy
 * concerns from general application wiring (Phase A.2).
 */
@Configuration
public class StrategyConfiguration {

    @Bean
    StrategyEngine strategyEngine(List<StrategyPlugin> strategyPlugins) {
        return new StrategyEngine(strategyPlugins);
    }
}
