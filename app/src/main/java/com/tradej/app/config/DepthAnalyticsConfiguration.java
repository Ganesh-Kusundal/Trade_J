package com.tradej.app.config;

import com.tradej.broker.core.depth.OrderBookEngine;
import com.tradej.execution.depth.*;
import com.tradej.gateway.bridge.GatewayEventBridge;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DepthAnalyticsConfiguration {

    @Bean
    public OrderBookEngine orderBookEngine() {
        return new OrderBookEngine();
    }

    @Bean
    public OrderBookImbalanceService orderBookImbalanceService() {
        return new OrderBookImbalanceService();
    }

    @Bean
    public HeatmapRecorder heatmapRecorder() {
        return new HeatmapRecorder();
    }

    @Bean
    public RestingOrderAnalyzer restingOrderAnalyzer() {
        return new RestingOrderAnalyzer();
    }

    @Bean
    public IcebergDetector icebergDetector() {
        return new IcebergDetector();
    }

    @Bean
    public AbsorptionAnalyzer absorptionAnalyzer() {
        return new AbsorptionAnalyzer();
    }

    @Bean
    public DepthAnalyticsPipeline depthAnalyticsPipeline(
            OrderBookEngine orderBookEngine,
            OrderBookImbalanceService imbalanceService,
            HeatmapRecorder heatmapRecorder,
            RestingOrderAnalyzer restingOrderAnalyzer,
            IcebergDetector icebergDetector,
            AbsorptionAnalyzer absorptionAnalyzer,
            ObjectProvider<GatewayEventBridge> bridgeProvider) {

        DepthAnalyticsPipeline pipeline = new DepthAnalyticsPipeline(
                orderBookEngine, imbalanceService, heatmapRecorder,
                restingOrderAnalyzer, icebergDetector, absorptionAnalyzer);

        // Wire analytics output to gateway WebSocket bridge
        bridgeProvider.ifAvailable(bridge -> pipeline.addConsumer(bridge::publishDepthAnalytics));

        // Wire OrderBookEngine depth updates to analytics pipeline
        orderBookEngine.addListener(pipeline::onDepthUpdate);

        return pipeline;
    }
}
