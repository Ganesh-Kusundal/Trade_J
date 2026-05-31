package com.tradej.app.config;

import com.tradej.app.service.broker.BrokerExpiredOptionQueryService;
import com.tradej.app.service.broker.BrokerHistoricalQueryService;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BrokerMarketDataConfiguration {

    @Bean
    @ConditionalOnBean(MarketDataProvider.class)
    BrokerHistoricalQueryService brokerHistoricalQueryService(MarketDataProvider marketDataProvider) {
        return new BrokerHistoricalQueryService(marketDataProvider);
    }

    @Bean
    @ConditionalOnBean(UpstoxExpiredOptionService.class)
    BrokerExpiredOptionQueryService brokerExpiredOptionQueryService(
            UpstoxExpiredOptionService upstoxExpiredOptionService
    ) {
        return new BrokerExpiredOptionQueryService(upstoxExpiredOptionService);
    }
}
