package com.tradej.app.config;

import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.ReconciliationHaltRequired;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.execution.risk.MarkToMarketRiskMonitor;
import com.tradej.execution.risk.PositionRiskHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures risk limits, the mark-to-market risk monitor,
 * and subscribes risk monitors to the domain event bus.
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

    /**
     * Subscribes risk monitors to the domain event bus.
     *
     * <p>Uses a bean method rather than {@code @PostConstruct} on the
     * configuration class to avoid circular dependency between this
     * configuration and the {@link MarkToMarketRiskMonitor} bean it defines.
     */
    @Bean
    RiskEventBusSubscriber riskEventBusSubscriber(
            EventBus eventBus,
            MarkToMarketRiskMonitor markToMarketRiskMonitor,
            PositionRiskHandler positionRiskHandler
    ) {
        return new RiskEventBusSubscriber(eventBus, markToMarketRiskMonitor, positionRiskHandler);
    }

    /**
     * Subscribes risk monitors to the event bus on construction,
     * breaking the circular dependency:
     * eventBus → pipeline → positionRiskHandler → markToMarketRiskMonitor → eventBus
     */
    static final class RiskEventBusSubscriber {

        RiskEventBusSubscriber(
                EventBus eventBus,
                MarkToMarketRiskMonitor markToMarketRiskMonitor,
                PositionRiskHandler positionRiskHandler
        ) {
            markToMarketRiskMonitor.setEventBus(eventBus);
            eventBus.subscribe(MarketTickEvent.class, markToMarketRiskMonitor::onMarketTick);
            eventBus.subscribe(ReconciliationHaltRequired.class, positionRiskHandler::handleReconciliationHalt);
        }
    }
}
