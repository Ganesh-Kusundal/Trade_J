package com.tradej.app.config;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.config.TradeDefaults;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.disruptor.config.BrokerScopedEventBus;
import com.tradej.app.health.MarketDataHealthIndicator;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.runtime.ExecutionModePolicy;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.ReplayTradingClock;
import com.tradej.core.domain.time.TradingClock;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.function.Consumer;

/**
 * Unified runtime configuration consolidating runtime mode,
 * event bus, and pipeline beans.
 */
@Configuration
public class RuntimeConfiguration {

    // ── Time and clocks ──

    @Bean
    @Profile("!replay")
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @Primary
    @Profile("!replay")
    public TradingClock liveTradingClock() {
        return new LiveTradingClock();
    }

    @Bean
    @Profile("replay")
    public TradingClock replayTradingClock() {
        return new ReplayTradingClock(Instant.EPOCH);
    }

    @Bean
    public EventMetadataFactory eventMetadataFactory(TradingClock tradingClock) {
        return new EventMetadataFactory(tradingClock);
    }

    // ── Runtime mode ──

    /**
     * Creates and configures the {@link RuntimeModeHolder}, applying the
     * configured {@link com.tradej.core.domain.runtime.RuntimeMode} at creation time.
     */
    @Bean
    RuntimeModeHolder runtimeModeHolder(TradingProperties properties) {
        RuntimeModeHolder holder = new RuntimeModeHolder();
        if (properties.runtime() != null) {
            holder.setMode(properties.runtime().mode());
        } else {
            holder.setMode(TradeDefaults.RUNTIME_MODE);
        }
        return holder;
    }

    @Bean
    LiveFallbackInfrastructureGuard liveFallbackInfrastructureGuard(RuntimeModeHolder modeHolder) {
        return new LiveFallbackInfrastructureGuard(modeHolder);
    }

    // ── Event bus ──

    @Lazy
    @Bean
    @Primary
    EventBus eventBus() {
        return new SimpleEventBus();
    }

    @Lazy
    @Bean("dhanEventBus")
    BrokerScopedEventBus dhanEventBus(EventBus eventBus) {
        return new BrokerScopedEventBus(eventBus, "dhan");
    }

    @Lazy
    @Bean("upstoxEventBus")
    BrokerScopedEventBus upstoxEventBus(EventBus eventBus) {
        return new BrokerScopedEventBus(eventBus, "upstox");
    }

    @Lazy
    @Bean("iciciEventBus")
    BrokerScopedEventBus iciciEventBus(EventBus eventBus) {
        return new BrokerScopedEventBus(eventBus, "icici");
    }

    @Lazy
    @Bean
    DisruptorBusMetrics disruptorBusMetrics(EventBus eventBus) {
        if (eventBus instanceof SimpleEventBus simple) {
            return new SimpleBusMetrics(simple);
        }
        return new NoOpBusMetrics();
    }

    @Lazy
    @Bean
    MarketDataPipeline marketDataPipeline(EventBus eventBus) {
        return new MarketDataPipeline((Consumer<DomainEvent>) eventBus::publish);
    }

    @Lazy
    @Bean
    OrderPipeline orderPipeline(EventBus eventBus) {
        return new OrderPipeline((Consumer<DomainEvent>) eventBus::publish);
    }

    @Bean
    MarketDataHealthIndicator marketDataHealthIndicator(
            @Lazy MarketDataPipeline marketDataPipeline,
            org.springframework.beans.factory.ObjectProvider<com.tradej.broker.api.model.BrokerTransportCapabilities> transportCapabilitiesProvider,
            com.tradej.app.health.AlertManager alertManager
    ) {
        return new MarketDataHealthIndicator(marketDataPipeline, transportCapabilitiesProvider, alertManager);
    }

    static final class LiveFallbackInfrastructureGuard implements BeanPostProcessor {

        private final RuntimeModeHolder modeHolder;

        LiveFallbackInfrastructureGuard(RuntimeModeHolder modeHolder) {
            this.modeHolder = modeHolder;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            ExecutionModePolicy policy = modeHolder.policy();
            if (policy.permitsFallbackInfrastructure()) {
                return bean;
            }
            Class<?> beanClass = bean.getClass();
            Package beanPackage = beanClass.getPackage();
            String packageName = beanPackage == null ? "" : beanPackage.getName();
            if (packageName.startsWith("com.tradej") && beanClass.getSimpleName().startsWith("NoOp")) {
                throw new IllegalStateException(
                        "LIVE mode forbids fallback infrastructure bean '" + beanName
                                + "' (" + beanClass.getName() + ")");
            }
            return bean;
        }
    }
}
