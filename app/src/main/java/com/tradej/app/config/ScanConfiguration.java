package com.tradej.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.composition.config.ScanProperties;
import com.tradej.app.scanner.RuntimeSubscriptionManager;
import com.tradej.app.scanner.ScanService;
import com.tradej.execution.subscription.SubscriptionCoordinator;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.persistence.duckdb.DuckDbScanStore;
import com.tradej.scanner.engine.ScanDependencies;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.institutional.InstitutionalScanEngine;
import com.tradej.scanner.engine.ScanDependencies;
import com.tradej.scanner.engine.ScanEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Optional;

@Configuration
@ConditionalOnProperty(prefix = "trade.scan", name = "enabled", havingValue = "true")
public class ScanConfiguration {

    @Bean
    ScanEngine scanEngine(ScanDependencies scanDependencies) {
        return new ScanEngine(scanDependencies);
    }

    @Bean
    DuckDbScanStore duckDbScanStore(TradingProperties tradingProperties) {
        return new DuckDbScanStore(Path.of(tradingProperties.storage().duckdbPath()));
    }

    @Bean
    ScanDependencies scanDependencies(IBrokerConnection brokerConnection) {
        return new ScanDependencies(
                brokerConnection.instruments(),
                brokerConnection.marketData(),
                brokerConnection.options(),
                brokerConnection.futures()
        );
    }

    @Bean
    RuntimeSubscriptionManager runtimeSubscriptionManager(
            IBrokerConnection brokerConnection,
            TradingProperties tradingProperties,
            @Autowired(required = false) SubscriptionCoordinator coordinator
    ) {
        return new RuntimeSubscriptionManager(brokerConnection.websocket(), coordinator, tradingProperties);
    }

    @Bean
    ScanService scanService(
            ScanProperties scanProperties,
            ScanDependencies scanDependencies,
            DuckDbScanStore scanStore,
            RuntimeSubscriptionManager subscriptionManager,
            @Autowired(required = false) GatewayTopicRouter gatewayRouter,
            ObjectMapper objectMapper,
            @Autowired(required = false) InstitutionalScanEngine institutionalScanEngine,
            @Autowired(required = false) HistoricalBarRepository historicalBarRepository
    ) {
        return new ScanService(
                scanProperties,
                scanDependencies,
                scanStore,
                subscriptionManager,
                Optional.ofNullable(gatewayRouter),
                objectMapper,
                Optional.ofNullable(institutionalScanEngine),
                Optional.ofNullable(historicalBarRepository)
        );
    }
}
