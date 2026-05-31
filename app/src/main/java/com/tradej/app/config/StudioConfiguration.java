package com.tradej.app.config;

import com.tradej.app.studio.StudioChartService;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;
import com.tradej.indicators.IndicatorEngine;
import com.tradej.institutional.InstitutionalScanEngine;
import com.tradej.institutional.model.InstitutionalScanConfig;
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
}
