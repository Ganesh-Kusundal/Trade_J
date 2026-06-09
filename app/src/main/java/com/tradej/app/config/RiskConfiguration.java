package com.tradej.app.config;

import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.execution.risk.MarkToMarketRiskMonitor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures risk limits and the mark-to-market risk monitor.
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
                risk.effectiveMaxOpenPositionQuantity(),
                risk.maxDistinctOpenPositions());
    }

    @Bean
    MarkToMarketRiskMonitor markToMarketRiskMonitor(
            TradingProperties properties,
            NetPositionProvider netPositionProvider
    ) {
        return new MarkToMarketRiskMonitor(
                properties.risk().enforceUnrealizedLoss(),
                netPositionProvider,
                properties.risk().maxDailyLossPaisa());
    }
}
