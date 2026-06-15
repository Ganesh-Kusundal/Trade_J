package com.tradej.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.app.scanner.OptionScanService;
import com.tradej.app.scanner.RuntimeSubscriptionManager;
import com.tradej.app.scanner.ScanService;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.app.config.ScanProperties;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.execution.subscription.SubscriptionCoordinator;
import com.tradej.execution.subscription.SubscriptionManager;
import com.tradej.execution.subscription.SubscriptionRecoveryManager;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.institutional.InstitutionalScanEngine;
import com.tradej.persistence.duckdb.DuckDbScanStore;
import com.tradej.scanner.engine.ScanDependencies;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.core.domain.port.EventBus;
import com.tradej.indicators.IndicatorEngine;
import com.tradej.institutional.model.InstitutionalScanConfig;
import com.tradej.replay.engine.CandleReplaySession;
import com.tradej.strategy.studio.StudioChartService;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Unified scan, option scan, and subscription configuration.
 *
 * <p>{@link ReconnectListenerRegistry} is a shared singleton that all broker
 * WebSocket multiplexers notify on reconnect. {@link SubscriptionRecoveryManager}
 * is registered as a listener to re-apply desired subscriptions after any reconnect.
 */
@Configuration
public class ScanConfiguration {

    private static final int DEFAULT_BATCH_SIZE = 50;

    // ── Studio and analytics ──

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

    // ── Subscription beans ──

    @Bean
    ReconnectListenerRegistry reconnectListenerRegistry() {
        return new ReconnectListenerRegistry();
    }

    @Bean
    SubscriptionCoordinator subscriptionCoordinator(
            IBrokerConnection brokerConnection
    ) {
        return new SubscriptionCoordinator(brokerConnection.websocket(), DEFAULT_BATCH_SIZE);
    }

    @Bean
    SubscriptionManager subscriptionManager(
            SubscriptionCoordinator subscriptionCoordinator,
            IBrokerConnection brokerConnection
    ) {
        return new SubscriptionManager(subscriptionCoordinator, brokerConnection.websocket());
    }

    @Bean
    SubscriptionRecoveryManager subscriptionRecoveryManager(
            SubscriptionManager subscriptionManager,
            ReconnectListenerRegistry reconnectListenerRegistry
    ) {
        SubscriptionRecoveryManager recovery = new SubscriptionRecoveryManager(subscriptionManager);
        reconnectListenerRegistry.addListener(recovery::recoverAfterReconnect);
        return recovery;
    }

    // ── Option scan ──

    @Bean
    OptionScanService optionScanService(IBrokerConnection brokerConnection) {
        return new OptionScanService(brokerConnection.options());
    }

    // ── Scan engine (conditional) ──

    @Bean
    @ConditionalOnProperty(prefix = "trade.scan", name = "enabled", havingValue = "true")
    ScanEngine scanEngine(ScanDependencies scanDependencies) {
        return new ScanEngine(scanDependencies);
    }

    @Bean
    @ConditionalOnProperty(prefix = "trade.scan", name = "enabled", havingValue = "true")
    DuckDbScanStore duckDbScanStore(TradingProperties tradingProperties) {
        return new DuckDbScanStore(Path.of(tradingProperties.storage().duckdbPath()));
    }

    @Bean
    @ConditionalOnProperty(prefix = "trade.scan", name = "enabled", havingValue = "true")
    ScanDependencies scanDependencies(IBrokerConnection brokerConnection) {
        return new ScanDependencies(
                brokerConnection.instruments(),
                brokerConnection.marketData(),
                brokerConnection.options(),
                brokerConnection.futures()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "trade.scan", name = "enabled", havingValue = "true")
    RuntimeSubscriptionManager runtimeSubscriptionManager(
            IBrokerConnection brokerConnection,
            TradingProperties tradingProperties,
            @Autowired(required = false) SubscriptionCoordinator coordinator
    ) {
        return new RuntimeSubscriptionManager(brokerConnection.websocket(), coordinator, tradingProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "trade.scan", name = "enabled", havingValue = "true")
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
                gatewayRouter,
                objectMapper,
                institutionalScanEngine,
                historicalBarRepository
        );
    }
}
