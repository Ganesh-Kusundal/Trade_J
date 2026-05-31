package com.tradej.app.config;

import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.execution.risk.PositionRiskHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures risk limits and the {@link PositionRiskHandler}.
 *
 * <p>The risk handler consumes {@link NetPositionProvider} for per-symbol position
 * qualification. It no longer depends on {@link PortfolioEngine} directly (PE-02).
 *
 * <p>Extracted from {@link TradingRuntimeConfiguration} to separate risk management
 * concerns from general application wiring (Phase A.2).
 */
@Configuration
public class RiskConfiguration {

    @Bean
    RiskLimits riskLimits(TradingProperties properties) {
        TradingProperties.RiskProperties risk = properties.risk();
        return new RiskLimits(
                risk.maxDailyLossPaisa(),
                risk.maxConsecutiveLosses(),
                risk.maxOrderValuePaisa(),
                risk.maxOpenPositions());
    }

    @Bean
    PositionRiskHandler positionRiskHandler(
            RiskLimits riskLimits,
            NetPositionProvider netPositionProvider) {
        return new PositionRiskHandler(riskLimits, netPositionProvider);
    }
}
