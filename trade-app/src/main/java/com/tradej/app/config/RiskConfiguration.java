package com.tradej.app.config;

import com.tradej.core.domain.model.RiskLimits;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures risk limits. {@link com.tradej.execution.risk.PositionRiskHandler}
 * is now discovered via {@code @Service} component scanning using the
 * {@link com.tradej.broker.api.port.InstrumentResolver} bean from
 * {@link BrokerConfiguration} (Phase A.3).
 *
 * <p>Extracted from {@link TradingRuntimeConfiguration} to separate risk
 * management concerns from general application wiring (Phase A.2).
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
                risk.maxOpenPositions()
        );
    }
}
