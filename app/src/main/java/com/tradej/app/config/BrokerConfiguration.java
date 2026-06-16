package com.tradej.app.config;

import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.api.spi.BrokerRegistry;
import com.tradej.broker.api.spi.ServiceLoaderBrokerRegistry;
import com.tradej.broker.upstox.expired.BrokerExpiredOptionQueryService;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionService;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.marketdata.LivePnlService;
import com.tradej.execution.marketdata.MarketDepthOrchestrator;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.historical.service.BrokerHistoricalQueryService;
import com.tradej.options.service.OptionStrikeResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Broker-agnostic core beans shared by all broker adapters.
 *
 * <p>Broker-specific wiring lives in separate files:
 * <ul>
 *   <li>{@link DhanBrokerConfiguration} — Dhan broker adapter</li>
 *   <li>{@link UpstoxBrokerConfiguration} — Upstox broker adapter</li>
 *   <li>{@link IciciAdapterConfiguration} — ICICI broker adapter</li>
 *   <li>{@link SimulationAdapterConfiguration} — Simulation/paper-trading adapter</li>
 *   <li>{@link GatewayConfiguration} — Gateway (WebSocket, routing, beans) — consolidated</li>
 *   <li>{@link MetricsConfiguration} — Prometheus + circuit-breaker + token gauges</li>
 * </ul>
 *
 * <p>Consolidated from:
 * <ul>
 *   <li>{@code BrokerMarketDataConfiguration} — historical/expired-option query services</li>
 *   <li>{@code BrokerResilienceMetricsConfiguration} — {@code BrokerResilienceMetrics} + circuit-breaker callback</li>
 * </ul>
 */
@Configuration
public class BrokerConfiguration {

    @Bean
    BrokerRegistry brokerRegistry() {
        return new ServiceLoaderBrokerRegistry();
    }

    // ── Broker-agnostic beans ──

    @Bean
    CaffeineIdempotencyCache idempotencyCache() {
        return new CaffeineIdempotencyCache();
    }

    @Bean
    @Primary
    OrderIdentityRegistry orderIdentityRegistry() {
        return new OrderIdentityRegistry();
    }

    @Bean
    @Primary
    BrokerCapabilities brokerCapabilities(TradingProperties properties) {
        return PropertiesBrokerCapabilities.from(properties.venues());
    }

    @Bean
    LivePnlService livePnlService(
            PortfolioProvider portfolioProvider,
            MarketDataProvider marketDataProvider,
            InstrumentResolver instrumentResolver
    ) {
        return new LivePnlService(portfolioProvider, marketDataProvider, instrumentResolver);
    }

    @Bean
    OptionStrikeResolver optionStrikeResolver(OptionsProvider optionsProvider) {
        return new OptionStrikeResolver(optionsProvider);
    }

    @Bean
    MarketDepthOrchestrator marketDepthOrchestrator(WebSocketMultiplexer webSocketMultiplexer) {
        return new MarketDepthOrchestrator(webSocketMultiplexer);
    }

    // ── Market data + historical query services ──
    // (Consolidated from BrokerMarketDataConfiguration)

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

    // Note: BrokerResilienceMetrics and CircuitBreakerMetrics beans live in
    // {@link MetricsConfiguration} to keep all metrics-related beans together.
}
