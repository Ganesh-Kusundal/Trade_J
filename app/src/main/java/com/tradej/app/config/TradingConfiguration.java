package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.core.domain.port.ModelRegistry;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.execution.command.CommandHandler;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.risk.KillSwitchCoordinator;
import com.tradej.execution.risk.MarginEnforcementHandler;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionConfig;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.feature.store.OptionsAwareFeatureStore;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.simulation.MatchingEngine;
import com.tradej.simulation.PnLLedger;
import com.tradej.simulation.SimulatedOrderService;
import com.tradej.strategy.api.GraphStrategyPlugin;
import com.tradej.strategy.example.DepthImbalanceStrategy;
import com.tradej.strategy.example.TickPriceChangeStrategy;
import com.tradej.strategy.ml.DefaultModelRegistry;
import com.tradej.strategy.ml.MLStrategyPlugin;
import com.tradej.strategy.ml.ThresholdMLInferenceEngine;
import com.tradej.strategy.plugin.OptionsContextStrategyPlugin;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.GraphStrategySandbox;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.ReconciliationHaltRequired;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.execution.risk.MarkToMarketRiskMonitor;
import java.time.Duration;
import java.util.List;

/**
 * Unified trading configuration consolidating strategy, execution,
 * portfolio, and simulation concerns.
 */
@Configuration
public class TradingConfiguration {

    // ── Risk beans ──

    @Bean
    com.tradej.core.domain.model.RiskLimits riskLimits(TradingProperties properties) {
        TradingProperties.RiskProperties risk = properties.risk();
        return new com.tradej.core.domain.model.RiskLimits(
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

    // ── Simulation beans ──

    @Bean
    MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }

    @Bean
    PnLLedger pnlLedger() {
        return new PnLLedger();
    }

    @Bean
    SimulatedOrderService simulatedOrderService(MatchingEngine matchingEngine, PnLLedger pnlLedger) {
        return new SimulatedOrderService(matchingEngine, pnlLedger);
    }

    // ── Portfolio ──

    @Bean
    PortfolioEngine portfolioEngine(TradingProperties properties) {
        TradingProperties.PortfolioProperties p = properties.portfolio();
        return new PortfolioEngine(
                p.defaultCapitalPaisa(),
                p.maxNetExposurePaisa()
        );
    }

    // ── Execution beans ──

    @Bean
    TradingCircuitBreaker tradingCircuitBreaker() {
        return new TradingCircuitBreaker();
    }

    @Bean
    @ConditionalOnMissingBean(OrderIdentityRegistry.class)
    OrderIdentityRegistry executionOrderIdentityRegistry() {
        return new OrderIdentityRegistry();
    }

    @Bean
    EventSourcedNetPositionProvider eventSourcedNetPositionProvider() {
        return new EventSourcedNetPositionProvider();
    }

    @Bean
    OrderManagementService orderManagementService(
            ObjectProvider<IBrokerConnection> brokerConnection,
            RuntimeModeHolder runtimeModeHolder,
            TradingClock tradingClock,
            ObjectProvider<SimulatedOrderService> simulatedOrderService,
            ObjectProvider<EventSourcedOrderRepository> orderRepository,
            TradingCircuitBreaker circuitBreaker
    ) {
        return new OrderManagementService(
                brokerConnection.getIfAvailable(),
                runtimeModeHolder,
                simulatedOrderService.getIfAvailable(),
                tradingClock,
                orderRepository.getIfAvailable(),
                circuitBreaker
        );
    }

    @Bean
    KillSwitchCoordinator killSwitchCoordinator(
            ObjectProvider<IBrokerConnection> brokerConnection,
            ObjectProvider<OrderManagementService> orderManagementService
    ) {
        return new KillSwitchCoordinator(
                brokerConnection.getIfAvailable(),
                orderManagementService.getIfAvailable()
        );
    }

    @Bean
    MarginEnforcementHandler marginEnforcementHandler(
            TradingProperties properties,
            ObjectProvider<IBrokerConnection> brokerConnection
    ) {
        var risk = properties.risk();
        if (!risk.enforceMargin()) {
            return new MarginEnforcementHandler(false, null, null, Duration.ofMinutes(1));
        }

        var connection = brokerConnection.getIfAvailable();
        var margin = connection == null
                ? null
                : connection.getCapability(com.tradej.broker.api.port.MarginProvider.class).orElse(null);
        var portfolio = connection == null
                ? null
                : connection.getCapability(com.tradej.broker.api.port.PortfolioProvider.class).orElse(null);

        return new MarginEnforcementHandler(
                true,
                margin,
                portfolio,
                Duration.ofMinutes(Math.max(1, risk.marginCacheTtlMinutes()))
        );
    }

    @Bean
    PositionRiskHandler positionRiskHandler(
            com.tradej.core.domain.model.RiskLimits riskLimits,
            EventSourcedNetPositionProvider netPositionProvider,
            ObjectProvider<PortfolioEngine> portfolioEngine,
            ObjectProvider<MarginEnforcementHandler> marginEnforcement,
            ObjectProvider<KillSwitchCoordinator> killSwitch
    ) {
        return new PositionRiskHandler(
                riskLimits,
                netPositionProvider,
                portfolioEngine.getIfAvailable(),
                marginEnforcement.getIfAvailable(),
                killSwitch.getIfAvailable()
        );
    }

    @Bean
    CommandHandler commandHandler(
            OrderManagementService orderManagementService,
            ObjectProvider<IBrokerConnection> brokerConnection
    ) {
        return new CommandHandler(orderManagementService, brokerConnection.getIfAvailable());
    }

    @Bean
    ExecutionHandler executionHandler(
            OrderManagementService orderManagementService,
            RuntimeModeHolder runtimeModeHolder,
            TradingClock tradingClock,
            TradingCircuitBreaker circuitBreaker,
            OrderIdentityRegistry identityRegistry,
            DeadLetterQueue deadLetterQueue
    ) {
        return new ExecutionHandler(
                orderManagementService,
                runtimeModeHolder,
                tradingClock,
                circuitBreaker,
                identityRegistry,
                deadLetterQueue,
                ExecutionConfig.DEFAULTS
        );
    }

    // ── Strategy beans ──

    @Bean
    ModelRegistry modelRegistry() {
        return new DefaultModelRegistry(List.of("threshold-rsi-ema"));
    }

    @Bean
    ThresholdMLInferenceEngine mlInferenceEngine(ModelRegistry registry) {
        return new ThresholdMLInferenceEngine(
                registry,
                ThresholdMLInferenceEngine.ThresholdConfig.defaults("threshold-rsi-ema")
        );
    }

    @Bean
    MLStrategyPlugin mlStrategyPlugin(FeatureStore featureStore, ThresholdMLInferenceEngine inferenceEngine) {
        return new MLStrategyPlugin(
                "ML-RSI-EMA",
                featureStore,
                inferenceEngine,
                "5m",
                20
        );
    }

    @Bean
    TickPriceChangeStrategy tickPriceChangeStrategy() {
        return new TickPriceChangeStrategy("Tick-Momentum", 50_00L, 5000L);
    }

    @Bean
    DepthImbalanceStrategy depthImbalanceStrategy() {
        return new DepthImbalanceStrategy("Depth-Imbalance", 2.0, 10_000L);
    }

    @Bean
    OptionsContextStrategyPlugin optionsContextStrategyPlugin(OptionsAwareFeatureStore featureStore) {
        return new OptionsContextStrategyPlugin(featureStore);
    }

    @Bean(destroyMethod = "shutdown")
    GraphStrategySandbox graphStrategySandbox(
            List<GraphStrategyPlugin> graphStrategyPlugins,
            EventMetadataFactory eventMetadataFactory
    ) {
        return new GraphStrategySandbox(graphStrategyPlugins, eventMetadataFactory);
    }
}
