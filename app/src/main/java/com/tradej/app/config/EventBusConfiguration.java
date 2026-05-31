package com.tradej.app.config;

import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.EventBus;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.app.pipeline.PipelineRuntimeService;
import com.tradej.app.health.MarketDataHealthIndicator;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.hotpath.PipelineConfig;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import com.tradej.strategy.service.StrategyEngine;
import com.tradej.feature.store.InMemoryFeatureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Configures the hot-path pipeline via the pure-Java {@link PipelineConfig}
 * factory and exposes the resulting components as Spring beans.
 *
 * <p>The actual pipeline wiring (DisruptorEventBus construction, pipeline
 * orchestration) lives in {@code trade-hotpath} module which has zero
 * Spring dependencies. This configuration class is the Spring-aware adapter
 * that resolves beans and delegates to the pure-Java factory.
 */
@Configuration
public class EventBusConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EventBusConfiguration.class);

    @Bean
    CandleAggregationService candleAggregationService(TradingProperties properties) {
        List<String> intervals = properties.candles().intervals();
        log.info("Configuring candle aggregation with intervals={}", intervals);
        return new CandleAggregationService(intervals);
    }

    @Bean
    PipelineConfig.PipelineComponents pipelineComponents(
            TradingProperties properties,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            InMemoryFeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue,
            PipelineRuntimeService pipelineRuntimeService
    ) {
        int shardCount = properties.hotPath().effectiveShardCount();
        log.info("Assembling hot-path pipeline via PipelineConfig shardCount={} graphRuntime=true graphStrategySandbox={}",
                shardCount, graphStrategySandbox != null);
        return PipelineConfig.create(
                shardCount,
                positionRiskHandler,
                candleAggregationService,
                strategyEngine,
                graphStrategySandbox,
                executionHandler,
                portfolioEngine,
                stageTimings,
                hotPathFeatureStore,
                deadLetterQueue,
                pipelineRuntimeService
        );
    }

    @Bean
    @Primary
    EventBus eventBus(PipelineConfig.PipelineComponents components) {
        return components.eventBus();
    }

    @Bean
    DisruptorBusMetrics disruptorBusMetrics(PipelineConfig.PipelineComponents components) {
        return components.busMetrics();
    }

    @Bean
    MarketDataPipeline marketDataPipeline(PipelineConfig.PipelineComponents components) {
        return components.marketDataPipeline();
    }

    @Bean
    OrderPipeline orderPipeline(PipelineConfig.PipelineComponents components) {
        return components.orderPipeline();
    }

    @Bean
    MarketDataHealthIndicator marketDataHealthIndicator(
            MarketDataPipeline marketDataPipeline,
            org.springframework.beans.factory.ObjectProvider<com.tradej.broker.api.model.BrokerTransportCapabilities> transportCapabilitiesProvider
    ) {
        return new MarketDataHealthIndicator(marketDataPipeline, transportCapabilitiesProvider);
    }
}
