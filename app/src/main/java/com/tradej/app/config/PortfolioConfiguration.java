package com.tradej.app.config;

import com.tradej.strategy.portfolio.PortfolioEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the portfolio engine with capital allocation and net exposure limits
 * from application properties.
 *
 * <p>The {@link PortfolioEngine} is wired into the Disruptor event pipeline between
 * the strategy stage and the execution stage so that every {@code SignalGenerated}
 * is validated against portfolio-level constraints before reaching the execution handler.
 */
@Configuration
public class PortfolioConfiguration {

    @Bean
    PortfolioEngine portfolioEngine(TradingProperties properties) {
        TradingProperties.PortfolioProperties p = properties.portfolio();
        return new PortfolioEngine(
                p.defaultCapitalPaisa(),
                p.maxNetExposurePaisa()
        );
    }
}
