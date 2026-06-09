package com.tradej.app.config;

import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.replay.engine.CandleReplaySession;
import com.tradej.strategy.studio.StudioChartService;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;
import com.tradej.indicators.IndicatorEngine;
import com.tradej.institutional.InstitutionalScanEngine;
import com.tradej.institutional.model.InstitutionalScanConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StudioConfiguration {

    @Bean
    InstitutionalScanEngine institutionalScanEngine(HistoricalBarRepository historicalBarRepository) {
        return new InstitutionalScanEngine(historicalBarRepository, InstitutionalScanConfig.baseline());
    }

    @Bean
    IndicatorEngine indicatorEngine() {
        return new IndicatorEngine();
    }

    @Bean
    StudioChartService studioChartService(
            HistoricalBarRepository historicalBarRepository,
            RollingOptionHistoricalRepository rollingOptionHistoricalRepository,
            InstitutionalScanEngine institutionalScanEngine,
            IndicatorEngine indicatorEngine
    ) {
        return new StudioChartService(
                historicalBarRepository,
                rollingOptionHistoricalRepository,
                institutionalScanEngine,
                indicatorEngine
        );
    }

    @Bean
    CandleReplaySession candleReplaySession(
            ObjectProvider<EventBus> eventBus,
            ObjectProvider<GatewayTopicRouter> gatewayRouter,
            ObjectMapper objectMapper
    ) {
        return new CandleReplaySession(
                Optional.ofNullable(eventBus.getIfAvailable()),
                Optional.ofNullable(gatewayRouter.getIfAvailable()),
                objectMapper
        );
    }
}
