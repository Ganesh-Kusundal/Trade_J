package com.tradej.app.config;

import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.core.domain.port.ModelRegistry;
import com.tradej.strategy.api.GraphStrategyPlugin;
import com.tradej.strategy.api.StrategyPlugin;
import com.tradej.strategy.api.StrategyPluginAdapter;
import com.tradej.strategy.example.DepthImbalanceStrategy;
import com.tradej.strategy.example.TickPriceChangeStrategy;
import com.tradej.strategy.ml.DefaultModelRegistry;
import com.tradej.strategy.ml.MLStrategyPlugin;
import com.tradej.strategy.ml.ThresholdMLInferenceEngine;
import com.tradej.strategy.service.GraphStrategySandbox;
import com.tradej.strategy.service.StrategyEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configures strategy-layer services.
 *
 * <p>{@link com.tradej.strategy.service.CandleAggregationService} is now
 * discovered via {@code @Service} component scanning (Phase A.3). Only beans
 * that require explicit wiring remain here.
 *
 * <p>Phase 1.2: Registers both legacy {@link StrategyPlugin} instances
 * (candle-only, via {@link StrategyPluginAdapter}) and native
 * {@link GraphStrategyPlugin} instances (tick/depth/multi-event) into the
 * unified {@link GraphStrategySandbox}. Legacy candle-only plugins continue
 * to work unchanged through the adapter.
 *
 * <p>Example plugins (TickPriceChangeStrategy, DepthImbalanceStrategy)
 * demonstrate the pattern for tick-level and depth-level strategies.
 */
@Configuration
public class StrategyConfiguration {

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

    /**
     * Creates the unified graph strategy sandbox with both legacy
     * {@link StrategyPlugin} instances (adapter-wrapped) and native
     * {@link GraphStrategyPlugin} instances.
     */
    @Bean(destroyMethod = "shutdown")
    GraphStrategySandbox graphStrategySandbox(
            List<StrategyPlugin> strategyPlugins,
            List<GraphStrategyPlugin> graphStrategyPlugins,
            EventMetadataFactory eventMetadataFactory
    ) {
        List<GraphStrategyPlugin> allPlugins = new ArrayList<>();
        // Wrap legacy candle-only plugins via adapter
        for (StrategyPlugin plugin : strategyPlugins) {
            allPlugins.add(new StrategyPluginAdapter(plugin));
        }
        // Add native graph strategy plugins (tick, depth, ML)
        allPlugins.addAll(graphStrategyPlugins);
        return new GraphStrategySandbox(allPlugins, eventMetadataFactory);
    }

    /**
     * Legacy {@link StrategyEngine} for backward compatibility.
     * Keeps existing callers working during the migration period.
     *
     * @deprecated Use {@link #graphStrategySandbox} for new strategy plugins.
     * Scheduled for removal after all plugins are migrated to {@link GraphStrategyPlugin}.
     */
    @Deprecated
    @Bean(destroyMethod = "shutdown")
    StrategyEngine strategyEngine(List<StrategyPlugin> strategyPlugins, EventMetadataFactory eventMetadataFactory) {
        return new StrategyEngine(strategyPlugins, eventMetadataFactory);
    }
}
