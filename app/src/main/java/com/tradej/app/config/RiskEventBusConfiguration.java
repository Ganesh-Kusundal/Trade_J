package com.tradej.app.config;

import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.ReconciliationHaltRequired;
import com.tradej.core.domain.port.EventBus;
import com.tradej.execution.risk.MarkToMarketRiskMonitor;
import com.tradej.execution.risk.PositionRiskHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Subscribes risk monitors to the domain event bus.
 */
@Configuration
public class RiskEventBusConfiguration {

    private final EventBus eventBus;
    private final MarkToMarketRiskMonitor markToMarketRiskMonitor;
    private final PositionRiskHandler positionRiskHandler;

    public RiskEventBusConfiguration(
            EventBus eventBus,
            MarkToMarketRiskMonitor markToMarketRiskMonitor,
            PositionRiskHandler positionRiskHandler
    ) {
        this.eventBus = eventBus;
        this.markToMarketRiskMonitor = markToMarketRiskMonitor;
        this.positionRiskHandler = positionRiskHandler;
    }

    @PostConstruct
    void registerSubscribers() {
        // Inject EventBus post-construction to break the circular dependency:
        // eventBus → pipeline → positionRiskHandler → markToMarketRiskMonitor → eventBus
        markToMarketRiskMonitor.setEventBus(eventBus);
        eventBus.subscribe(MarketTickEvent.class, markToMarketRiskMonitor::onMarketTick);
        eventBus.subscribe(ReconciliationHaltRequired.class, positionRiskHandler::handleReconciliationHalt);
    }
}
