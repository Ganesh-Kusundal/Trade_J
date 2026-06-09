package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.execution.command.CommandHandler;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.risk.KillSwitchCoordinator;
import com.tradej.execution.risk.MarginEnforcementHandler;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.simulation.SimulatedOrderService;
import com.tradej.strategy.portfolio.PortfolioEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ExecutionBeansConfiguration {

    @Bean
    TradingCircuitBreaker tradingCircuitBreaker() {
        return new TradingCircuitBreaker();
    }

    @Bean
    @ConditionalOnMissingBean(OrderIdentityRegistry.class)
    OrderIdentityRegistry orderIdentityRegistry() {
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
                deadLetterQueue
        );
    }
}
