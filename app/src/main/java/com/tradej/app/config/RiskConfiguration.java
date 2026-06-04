package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.execution.risk.KillSwitchCoordinator;
import com.tradej.execution.risk.MarginEnforcementHandler;
import com.tradej.execution.risk.MarkToMarketRiskMonitor;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.strategy.portfolio.PortfolioEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configures risk limits and the {@link PositionRiskHandler}.
 *
 * <p>The risk handler consumes {@link NetPositionProvider} for per-symbol position
 * qualification and {@link PortfolioEngine} for capital reservation before execution.
 *
 * <p>Separates risk management concerns from general application wiring (Phase A.2).
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
    MarginEnforcementHandler marginEnforcementHandler(
            TradingProperties properties,
            ObjectProvider<IBrokerConnection> brokerConnection
    ) {
        TradingProperties.RiskProperties risk = properties.risk();
        IBrokerConnection connection = brokerConnection.getIfAvailable();
        MarginProvider margin = connection == null
                ? null
                : connection.getCapability(MarginProvider.class).orElse(null);
        PortfolioProvider portfolio = connection == null
                ? null
                : connection.getCapability(PortfolioProvider.class).orElse(null);
        return new MarginEnforcementHandler(
                risk.enforceMargin(),
                margin,
                portfolio,
                Duration.ofMinutes(Math.max(1, risk.marginCacheTtlMinutes())));
    }

    @Bean
    KillSwitchCoordinator killSwitchCoordinator(
            ObjectProvider<IBrokerConnection> brokerConnection,
            ObjectProvider<OrderManagementService> orderManagementService
    ) {
        return new KillSwitchCoordinator(
                brokerConnection.getIfAvailable(),
                orderManagementService.getIfAvailable());
    }

    @Bean
    PositionRiskHandler positionRiskHandler(
            RiskLimits riskLimits,
            NetPositionProvider netPositionProvider,
            PortfolioEngine portfolioEngine,
            ObjectProvider<MarginEnforcementHandler> marginEnforcement,
            ObjectProvider<KillSwitchCoordinator> killSwitchCoordinator,
            ObjectProvider<MarkToMarketRiskMonitor> mtmMonitor,
            TradingProperties properties
    ) {
        PositionRiskHandler handler = new PositionRiskHandler(
                riskLimits,
                netPositionProvider,
                portfolioEngine,
                marginEnforcement.getIfAvailable(),
                killSwitchCoordinator.getIfAvailable());
        mtmMonitor.ifAvailable(m -> m.registerRiskHandler(handler));
        return handler;
    }

    @Bean
    MarkToMarketRiskMonitor markToMarketRiskMonitor(
            TradingProperties properties,
            NetPositionProvider netPositionProvider
    ) {
        // EventBus is injected post-construction by RiskEventBusConfiguration
        // to break the circular dependency:
        // eventBus → pipeline → positionRiskHandler → markToMarketRiskMonitor → eventBus
        return new MarkToMarketRiskMonitor(
                properties.risk().enforceUnrealizedLoss(),
                netPositionProvider,
                properties.risk().maxDailyLossPaisa());
    }
}
